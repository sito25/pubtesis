/*
 * Copyright 2010 Aalto University, ComNet
 * Released under GPLv3. See LICENSE.txt for details.
 */
package routing;

import java.util.*;
import util.Tuple;
import core.Connection;
import core.DTNHost;
import core.Message;
import core.Settings;
import core.SimClock;

/**
 * Epidemic message router with drop-oldest buffer and only single transferring
 * connections at a time.
 */
public class TimeoutFriendsRouter extends ActiveRouter {
	/** how often TTL check (discarding old messages) is performed */
	public static int FRIENDS_CLOCK_CHECK_INTERVAL = 500;
	/** sim time when the last TTL check was done */
	private double lastFriendsClockCheck;
	
	/**
	 * Constructor. Creates a new message router based on the settings in
	 * the given Settings object.
	 * @param s The settings object
	 */
	public TimeoutFriendsRouter(Settings s) {
		super(s);
		this.lastFriendsClockCheck = 0;
	}

	/**
	 * Copy constructor.
	 * @param r The router prototype where setting values are copied from
	 */
	protected TimeoutFriendsRouter(TimeoutFriendsRouter r) {
		super(r);
		this.lastFriendsClockCheck = 0;
	}

	@Override
	public void update() {
		super.update();
		if (isTransferring() || !canStartTransfer()) {
			return; // transferring, don't try other connections yet
		}

		// Try first the messages that can be delivered to final recipient
		if (exchangeDeliverableMessages() != null) {
			return; // started a transfer, don't try others (yet)
		}

		// then try any/all message to any/all connection
		tryOtherMessages();
		
		/* time to do a TTL check and drop old messages? Only if not sending */
		if (SimClock.getTime() - this.lastFriendsClockCheck >= FRIENDS_CLOCK_CHECK_INTERVAL && sendingConnections.size() == 0) {
			dropExpiredFriends();
			this.lastFriendsClockCheck = SimClock.getTime();
		}
	}
	
	/**
	 * Drops friends whose timeout is less than zero.
	 */
	protected void dropExpiredFriends() {
		/*Set<String> friends = this.getHost().getFriends().keySet();
		Iterator<String> i = friends.iterator();
		
		while (i.hasNext()){
			String friend = i.next();
			if (this.getHost().expiredFriend(friend)){
				this.getHost().deleteFriend(friend);
			}
		}*/
	}

	private Tuple<Message, Connection> tryOtherMessages() {
		List<Tuple<Message, Connection>> messages = new ArrayList<Tuple<Message, Connection>>();

		Collection<Message> msgCollection = getMessageCollection();
		
		/* for all connected hosts collect all messages that have a higher
		   probability of delivery by the other host */
		
		for (Connection con : getConnections()) {
			DTNHost other = con.getOtherNode(getHost());
			TimeoutFriendsRouter othRouter = (TimeoutFriendsRouter)other.getRouter();

			if (othRouter.isTransferring()) {
				continue; // skip hosts that are transferring
			}
			
			//System.out.println(con);
			//System.out.println(other);
			for (Message m : msgCollection) {
				if (othRouter.hasMessage(m.getId())) {
					continue; // skip messages that the other one has
				}
				DTNHost messageDestination = m.getTo();
				System.out.println("New message: " + m + " send to " + m.getTo());
				//other.showListOfFriends();
				
				String destination = messageDestination.getName();
				/*if (other.hostHasFriend(destination) && !other.expiredFriend(destination)){
					messages.add(new Tuple<Message, Connection>(m,con));
					System.out.println(" **** ENTRA ****");
				}*/
			}
		}

		if (messages.size() == 0) {
			return null;
		}

		// sort the message-connection tuples
		this.sortByQueueMode(messages);
		
		return tryMessagesForConnected(messages);	// try to send messages
	}
	
	@Override
	public TimeoutFriendsRouter replicate() {
		return new TimeoutFriendsRouter(this);
	}

}
