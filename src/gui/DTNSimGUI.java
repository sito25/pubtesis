/*
 * Copyright 2010 Aalto University, ComNet
 * Released under GPLv3. See LICENSE.txt for details.
 */
package gui;

import gui.playfield.PlayField;

import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.lang.reflect.InvocationTargetException;

import javax.swing.JOptionPane;
import javax.swing.JScrollPane;
import javax.swing.SwingUtilities;

import movement.Path;
import ui.DTNSimUI;
import core.Coord;
import core.DTNHost;
import core.Friend;
import core.SimClock;

import java.util.*;

/**
 * Graphical User Interface for simulator
 */
public class DTNSimGUI extends DTNSimUI {
	private MainWindow main;
	private PlayField field;
	private GUIControls guiControls;
	private EventLogPanel eventLogPanel;
	private InfoPanel infoPanel;

	private void startGUI() {
		try {
			SwingUtilities.invokeAndWait(new Runnable() {
			    public void run() {
					try {
						initGUI();
					} catch (AssertionError e) {
						processAssertionError(e);
					}
			    }
			});
		} catch (InterruptedException e) {
			e.printStackTrace();
			System.exit(-1);
		} catch (InvocationTargetException e) {
			e.printStackTrace();
			System.exit(-1);
		}
	}

	/**
	 * Initializes the GUI
	 */
	private void initGUI() {
		this.field = new PlayField(world, this);

		this.field.addMouseListener(new PlayfieldMouseHandler());
		this.field.addMouseWheelListener(new PlayfieldMouseHandler());

		this.guiControls = new GUIControls(this,this.field);
		this.eventLogPanel = new EventLogPanel(this);
		this.infoPanel = new InfoPanel(this);
		this.main = new MainWindow(this.scen.getName(), world, field,
				guiControls, infoPanel, eventLogPanel, this);

		scen.addMessageListener(eventLogPanel);
		scen.addConnectionListener(eventLogPanel);

		if (scen.getMap() != null ) {
			field.setMap(scen.getMap());
		}

		// if user closes the main window, call closeSim()
		this.main.addWindowListener(new WindowAdapter() {
			private boolean closeAgain = false;
			public void windowClosing(WindowEvent e)  {
				closeSim();
				if (closeAgain) {
					// if method is called again, force closing
					System.err.println("Forced close. "+
							"Some reports may have not been finalized.");
					System.exit(-1);
				}
				closeAgain = true;
			}
		});

		this.main.setVisible(true);
	}

	@Override
	protected void runSim() {
		double simTime = SimClock.getTime();
		double endTime = scen.getEndTime();

		startGUI();

		// Startup DTN2Manager
		// XXX: Would be nice if this wasn't needed..
		// DTN2Manager.setup(world);

		while (simTime < endTime && !simCancelled){
			if (guiControls.isPaused()) {
				wait(10); // release CPU resources when paused
			}
			else {
				try {
					world.update();
					
				} catch (AssertionError e) {
					// handles both assertion errors and SimErrors
					processAssertionError(e);
				}
				simTime = SimClock.getTime();
			}
			this.update(false);
		}		
		
		simDone = true;

		/*
		List<DTNHost> hosts = world.getHosts();
		Iterator<DTNHost> it = hosts.iterator();
		double globalTemporality = 0.0;
		while (it.hasNext()){
			DTNHost host = it.next();
			double totalTemporalLocality = host.getTotalTemporalLocality();
			System.out.println(host.getName() + ": " + totalTemporalLocality);
			globalTemporality += totalTemporalLocality;
		}
		System.out.println(globalTemporality / hosts.size());
		*/
//		List<DTNHost> hosts2 = world.getHosts();
//		Iterator<DTNHost> it2 = hosts2.iterator();
//		while (it2.hasNext()){
//			DTNHost host = it2.next();
//			Set<String> friends = host.getFriends();
//			Iterator<String>it3 = friends.iterator();
//			while(it3.hasNext()) {
//				String friendSt = it3.next();
//				Friend friend = host.getFriend(friendSt);
//				double temporalLocality = 0.0;
//				int timesR = 0;
//				if (friend!=null){ 
//					temporalLocality = friend.getTemporalLocality();
//					timesR = friend.getTimesRenewed();
//				}
//				System.out.println(host.getName() + ", " + friendSt + ", " + ": " + timesR + ", " + temporalLocality);
//			}
//		}
		
		done();
		this.update(true); // force final GUI update

		if (!simCancelled) { // NOT cancelled -> leave the GUI running
			JOptionPane.showMessageDialog(getParentFrame(),
					"Simulation done");
		}
		else { // was cancelled -> exit immediately
			System.exit(0);
		}
	}

	/**
	 * Processes assertion errors by showing a warning dialog to the user
	 * and pausing the simulation (if it's running)
	 * @param e The error that was thrown
	 */
	private void processAssertionError(AssertionError e) {
		String title = e.getClass().getSimpleName() + " (simulation paused)";
		String msg = e.getMessage();
		String txt = (msg != null ? msg : "") + " at simtime " +
			SimClock.getIntTime() +	"\n\ncaught at:\n" +
			e.getStackTrace()[0].toString() +
			"\nNote that the simulation might be in inconsistent state, "+
			"continue only with caution.\n\n Show rest of the stack trace?";
		// rest of the update cycle that caused the exception is skipped
		// so the user is warned about the consequences


		if (guiControls != null) {
			guiControls.setPaused(true);
		}

		int selection = JOptionPane.showOptionDialog(getParentFrame(), txt,
				title, JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE,
				null, null, null);

		if (selection == 0) {
			txt = "";
			for (StackTraceElement trace : e.getStackTrace()) {
				txt += trace.toString()+"\n";
			}
			JOptionPane.showMessageDialog(getParentFrame(), txt,
				"stack trace", JOptionPane.INFORMATION_MESSAGE);
		}
	}



	/**
	 * Closes the program if simulation is done or cancels it.
	 */
	public void closeSim() {
		if (simDone) {
			System.exit(0);
		}
		this.world.cancelSim();
		this.simCancelled = true;
	}

    /**
     * Updates the GUI
     */
    public void update(boolean forcedUpdate) {
	double guiUpdateInterval = guiControls.getUpdateInterval();

	// update only if long enough simTime has passed (and not forced)
		if (!forcedUpdate && guiUpdateInterval > (SimClock.getTime()
				- this.lastUpdate)) {
			return;
		}

		try {
			// run update in EDT, TODO: optimize threading
			SwingUtilities.invokeAndWait(new Runnable() {
			    public void run() {
					updateView();
			    }
			});
		} catch (InterruptedException e) {
			e.printStackTrace();
		} catch (InvocationTargetException e) {
			e.printStackTrace();
		}

	// wait a while if we don't want to run simulation at full speed
	if (guiUpdateInterval < 0) {
		wait(100*(int)(-guiUpdateInterval));
	}

    }

    /**
     * Updates playfield and sim time field
     *
     */
    private void updateView() {
	double simTime = SimClock.getTime();
	this.lastUpdate = simTime;
	guiControls.setSimTime(simTime); //update time to control panel

	this.field.updateField();
    }

    /**
     * Sets the pause of the simulation on/off
     * @param paused True if pause should be set on
     */
    public void setPaused(boolean paused) {
	this.guiControls.setPaused(paused);
    }

    /**
     * Sets a node's graphical presentation in the center of the playfield view
     * @param host The node to center
     */
    public void setFocus(DTNHost host) {
	centerViewAt(host.getLocation());
	infoPanel.showInfo(host);
	showPath(host.getPath()); // show path on the playfield
    }

    /**
     * Shows a path on the playfield
     * @param path The path to show
     */
    public void showPath(Path path) {
	field.addPath(path);
    }

    /**
     * Returns the world coordinates that are currently in the center
     * of the viewport
     * @return The coordinates
     */
    public Coord getCenterViewCoord() {
	JScrollPane sp = main.getPlayFieldScroll();
	double midX, midY;

	midX = sp.getHorizontalScrollBar().getValue() +
		sp.getViewport().getWidth()/2;
	midY = sp.getVerticalScrollBar().getValue() +
		sp.getViewport().getHeight()/2;

	return this.field.getWorldPosition(new Coord(midX, midY));
    }

    /**
     * Sets certain location to be in the center of the playfield view
     * @param loc The location to center
     */
    public void centerViewAt(Coord loc) {
	JScrollPane sp = main.getPlayFieldScroll();
	Coord gLoc = this.field.getGraphicsPosition(loc);
	int midX, midY;

	updateView(); // update graphics to match the values

	midX = (int)gLoc.getX() - sp.getViewport().getWidth()/2;
	midY = (int)gLoc.getY() - sp.getViewport().getHeight()/2;

	sp.getHorizontalScrollBar().setValue(midX);
	sp.getVerticalScrollBar().setValue(midY);
    }

    /**
     * Returns the info panel of the GUI
     * @return the info panel of the GUI
     */
    public InfoPanel getInfoPanel() {
	return this.infoPanel;
    }

    /**
     * Returns the parent frame (window) of the gui.
     * @return The parent frame
     */
    public MainWindow getParentFrame() {
	return this.main;
    }

	/**
	 * Suspend thread for ms milliseconds
	 * @param ms The nrof milliseconds to wait
	 */
	private void wait(int ms) {
		try {
			Thread.sleep(ms);
		} catch (InterruptedException e) {
			// nothing to do here
		}
	}

	/**
	 * Handler for playfield's mouse clicks.
	 */
	private class PlayfieldMouseHandler extends MouseAdapter implements
		MouseWheelListener {
		/**
		 * If mouse button is clicked, centers view at that location.
		 */
		public void mouseClicked(MouseEvent e) {

			java.awt.Point p = e.getPoint();
			centerViewAt(field.getWorldPosition(new Coord(p.x, p.y)));
		}

		public void mouseWheelMoved(java.awt.event.MouseWheelEvent e) {
			guiControls.changeZoom(e.getWheelRotation());
		}
	}

}
