/*
 * Copyright 2010 Aalto University, ComNet
 * Released under GPLv3. See LICENSE.txt for details.
 */
package routing;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import util.Tuple;
import core.Connection;
import core.DTNHost;
import core.Message;
import core.Settings;

/**
 * Epidemic message router with drop-oldest buffer and only single transferring
 * connections at a time.
 */
public class BasicFriendsRouter extends ActiveRouter {

	/**
	 * Constructor. Creates a new message router based on the settings in
	 * the given Settings object.
	 * @param s The settings object
	 */
	public BasicFriendsRouter(Settings s) {
		super(s);
		//TODO: read&use epidemic router specific settings (if any)
	}

	/**
	 * Copy constructor.
	 * @param r The router prototype where setting values are copied from
	 */
	protected BasicFriendsRouter(BasicFriendsRouter r) {
		super(r);
		//TODO: copy epidemic settings here (if any)
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
	}

	private Tuple<Message, Connection> tryOtherMessages() {
		List<Tuple<Message, Connection>> messages = new ArrayList<Tuple<Message, Connection>>();

		Collection<Message> msgCollection = getMessageCollection();
		
		/* for all connected hosts collect all messages that have a higher
		   probability of delivery by the other host */
		
		for (Connection con : getConnections()) {
			DTNHost other = con.getOtherNode(getHost());
			BasicFriendsRouter othRouter = (BasicFriendsRouter)other.getRouter();

			if (othRouter.isTransferring()) {
				continue; // skip hosts that are transferring
			}
			
			System.out.println(con);
			System.out.println(other);
			for (Message m : msgCollection) {
				if (othRouter.hasMessage(m.getId())) {
					continue; // skip messages that the other one has
				}
				DTNHost messageDestination = m.getTo();
				System.out.println("New message: " + m + " send to " + m.getTo());
				//if (other.hostHasFriend(messageDestination.getName())){
				//	messages.add(new Tuple<Message, Connection>(m,con));
				//	System.out.println(" **** ENTRA ****");
				//}
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
	public BasicFriendsRouter replicate() {
		return new BasicFriendsRouter(this);
	}

}
