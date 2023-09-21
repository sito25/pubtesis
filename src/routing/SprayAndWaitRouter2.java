/*
 * Copyright 2010 Aalto University, ComNet
 * Released under GPLv3. See LICENSE.txt for details.
 */
package routing;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import core.Connection;
import core.DTNHost;
import core.Message;
import core.Settings;

//import java.lang.NullPointerException;

/**
 * Implementation of Spray and wait router as depicted in
 * <I>Spray and Wait: An Efficient Routing Scheme for Intermittently
 * Connected Mobile Networks</I> by Thrasyvoulos Spyropoulus et al.
 *
 */
public class SprayAndWaitRouter2 extends ActiveRouter {
	/** identifier for the initial number of copies setting ({@value})*/
	public static final String NROF_COPIES = "nrofCopies";
	public static final String NUMBER_OF_NODES = "numberOfNodes";
	public static final String MATRIX_FILE = "matrixFile";
	/** identifier for the binary-mode setting ({@value})*/
	public static final String BINARY_MODE = "binaryMode";
	/** SprayAndWait router's settings name space ({@value})*/
	public static final String SPRAYANDWAIT_NS2 = "SprayAndWaitRouter2";
	/** Message property key */
	public static final String MSG_COUNT_PROPERTY = SPRAYANDWAIT_NS2 + "." + "copies";

	protected int initialNrofCopies;
	protected boolean isBinary;
	protected int numberOfNodes;
	protected String matrixFile;
	protected ArrayList<String> chosenNodes;
	
	
	public SprayAndWaitRouter2(Settings s) {
		super(s);
		
		Settings snwSettings = new Settings(SPRAYANDWAIT_NS2);

		initialNrofCopies = snwSettings.getInt(NROF_COPIES);
		isBinary = snwSettings.getBoolean( BINARY_MODE);
		numberOfNodes = snwSettings.getInt(NUMBER_OF_NODES);
		matrixFile = snwSettings.getSetting(MATRIX_FILE);
	}

	/**
	 * Copy constructor.
	 * @param r The router prototype where setting values are copied from
	 */
	protected SprayAndWaitRouter2(SprayAndWaitRouter2 r) {
		super(r);
		this.initialNrofCopies = r.initialNrofCopies;
		this.isBinary = r.isBinary;
		this.numberOfNodes = r.numberOfNodes;
		this.matrixFile = r.matrixFile;
		
		this.chosenNodes = read_first_10_percent_nodes(matrixFile, numberOfNodes);
	}

	@Override
	public int receiveMessage(Message m, DTNHost from) {
		return super.receiveMessage(m, from);
	}

	@Override
	public Message messageTransferred(String id, DTNHost from) {
		Message msg = super.messageTransferred(id, from);
		Integer nrofCopies = (Integer)msg.getProperty(MSG_COUNT_PROPERTY);

		assert nrofCopies != null : "Not a SnW message: " + msg;

		if (isBinary) {
			/* in binary S'n'W the receiving node gets floor(n/2) copies */
			nrofCopies = (int)Math.floor(nrofCopies/2.0);
		}
		else {
			/* in standard S'n'W the receiving node gets only single copy */
			nrofCopies = 1;
		}

		msg.updateProperty(MSG_COUNT_PROPERTY, nrofCopies);
		return msg;
	}

	@Override
	public boolean createNewMessage(Message msg) {
		makeRoomForNewMessage(msg.getSize());
		
		DTNHost from = msg.getFrom();
		DTNHost to = msg.getTo();
		
		from.updateTwoFriendsTemporalLocality(to.getName());
		
		Integer newNumberOfCopies = new Integer(initialNrofCopies);

		msg.setTtl(this.msgTtl);
		msg.addProperty(MSG_COUNT_PROPERTY, newNumberOfCopies);
		msg.addProperty("EPIDEMIC_MESSAGE", false);
		addToMessages(msg, true);
		return true;
	}

	@Override
	public void update() {
		super.update();
		
		if (!canStartTransfer() || isTransferring()) {
			return; // nothing to transfer or is currently transferring
		}

		/* try messages that could be delivered to final recipient */
		if (exchangeDeliverableMessages() != null) {
			return;
		}

		/* create a list of SAWMessages that have copies left to distribute */
		@SuppressWarnings(value = "unchecked")
		List<Message> copiesLeft = sortByQueueMode(getMessagesWithCopiesLeft());

		if (copiesLeft.size() > 0) {
			/* try to send those messages */
			this.tryMessagesToConnections(copiesLeft, getConnections());
		}
	}
	
	/**
	 * Tries to send all given messages to all given connections. Connections
	 * are first iterated in the order they are in the list and for every
	 * connection, the messages are tried in the order they are in the list.
	 * Once an accepting connection is found, no other connections or messages
	 * are tried.
	 * @param messages The list of Messages to try
	 * @param connections The list of Connections to try
	 * @return The connections that started a transfer or null if no connection
	 * accepted a message.
	 */
	
	protected Connection tryMessagesToConnections(List<Message> messages,
			List<Connection> connections) {
		for (int i=0, n=connections.size(); i<n; i++) {
			Connection con = connections.get(i);
			Message started = tryAllMessages(con, messages);
			if (started != null) {
				return con;
			}
		}

		return null;
	}
	
	
	/**
	 * Creates and returns a list of messages this router is currently
	 * carrying and still has copies left to distribute (nrof copies > 1).
	 * @return A list of messages that have copies left
	 */
	protected List<Message> getMessagesWithCopiesLeft() {
		List<Message> list = new ArrayList<Message>();

		for (Message m : getMessageCollection()) {
			Integer nrofCopies = (Integer)m.getProperty(MSG_COUNT_PROPERTY);
			assert nrofCopies != null : "SnW message " + m + " didn't have " +
				"nrof copies property!";
			if (nrofCopies > 1) {
				list.add(m);
			}
		}

		return list;
	}
	
	/**
	  * Goes trough the messages until the other node accepts one
	  * for receiving (or doesn't accept any). If a transfer is started, the
	  * connection is included in the list of sending connections.
	  * @param con Connection trough which the messages are sent
	  * @param messages A list of messages to try
	  * @return The message whose transfer was started or null if no
	  * transfer was started.
	  */
	protected Message tryAllMessages(Connection con, List<Message> messages) {		
		for (Message m : messages) {
			String endHostName = con.getTo().getName();
			String messageDestination = m.getTo().getName();
			if (chosenNodes.contains(endHostName) || (m.getFrom().isFriend(messageDestination)) || (endHostName.compareTo(messageDestination) == 0))
			{			
				int retVal = startTransfer(m, con);
				if (retVal == RCV_OK) {
					return m;	// accepted a message, don't try others
				}
				else if (retVal > 0) {
					return null; // should try later -> don't bother trying others
				}
			}
			//else
			/*{
				m.updateProperty("EPIDEMIC_MESSAGE", true);
				
				int retVal = startTransfer(m, con);
				if (retVal == RCV_OK) {
					return m;	// accepted a message, don't try others
				}
				else if (retVal > 0) {
					return null; // should try later -> don't bother trying others
				}
				
				return m;
			}*/
		}

		return null; // no message was accepted
	}
	
	
	public ArrayList<String> read_first_10_percent_nodes(String file, int numberOfNodes) 
	{
		String line;
		BufferedReader reader;
		int numberOfNodesReaded = 0;
		ArrayList<String> nodes = new ArrayList<String>();
		
		try 
		{
			reader = new BufferedReader(new FileReader(file));
			do 
			{
				line = reader.readLine();
				if (line != null) {
					int node = (Integer.parseInt(line)) - 1;
					String nodeS = "p" + node;
					nodes.add(nodeS);
					numberOfNodesReaded++;
				}
			}
			while (line != null && numberOfNodesReaded < numberOfNodes);
			reader.close();
		} catch (IOException e) 
		{
			e.printStackTrace();
		}
		return nodes;
	}
	

	/**
	 * Called just before a transfer is finalized (by
	 * {@link ActiveRouter#update()}).
	 * Reduces the number of copies we have left for a message.
	 * In binary Spray and Wait, sending host is left with floor(n/2) copies,
	 * but in standard mode, nrof copies left is reduced by one.
	 */
	@Override
	protected void transferDone(Connection con) {
		Integer nrofCopies;
		String msgId = con.getMessage().getId();
		/* get this router's copy of the message */
		Message msg = getMessage(msgId);

		if (msg == null) { // message has been dropped from the buffer after..
			return; // ..start of transfer -> no need to reduce amount of copies
		}

		/* reduce the amount of copies left */
		nrofCopies = (Integer)msg.getProperty(MSG_COUNT_PROPERTY);
		if (isBinary) {
			/* in binary S'n'W the sending node keeps ceil(n/2) copies */
			nrofCopies = (int)Math.ceil(nrofCopies/2.0);
		}
		else {
			//boolean epidemic_message = (boolean)msg.getProperty("EPIDEMIC_MESSAGE");
			//if (!epidemic_message)
			//{
				nrofCopies--;
			//}
			//else
			//{
			//	msg.updateProperty("EPIDEMIC_MESSAGE", false);
			//}
		}
		msg.updateProperty(MSG_COUNT_PROPERTY, nrofCopies);
	}

	@Override
	public SprayAndWaitRouter2 replicate() {
		return new SprayAndWaitRouter2(this);
	}
}
