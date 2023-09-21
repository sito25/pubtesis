package core;

public class Friend {
	private double contactTimeout;
	private int timesRenewed;
	private double temporalLocality;
	private int numberOfCopiesAllowed;
	
	public Friend (int numberOfCopiesAllowed){
		this.timesRenewed = 0;
		this.temporalLocality = 0.0;
		this.numberOfCopiesAllowed = numberOfCopiesAllowed;
	}
	
	public double getContactTimeout (){
		return this.contactTimeout;
	}
	
	public int getTimesRenewed (){
		return this.timesRenewed;
	}
	
	public double getTemporalLocality (){
		return this.temporalLocality;
	}
	
	public void setContactTimeout (double contactTimeout){
		this.contactTimeout = contactTimeout;
	}
	
	public void setTimesRenewed (int timesRenewed){
		this.timesRenewed = timesRenewed;
	}
	
	public void updateTemporalLocality(){
		this.temporalLocality = this.timesRenewed / SimClock.getTime();
	}
	
	public int getNumberOfCopiesAllowed() {
		return this.numberOfCopiesAllowed;
	}
}
