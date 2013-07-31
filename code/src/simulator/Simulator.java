package simulator;

import java.io.IOException;
import java.util.Calendar;
import java.util.List;

import common.UpdateData;
import common.Vessel;

public class Simulator {
	SimulatorConfiguration _Configuration;
	
	public Simulator(SimulatorConfiguration config) {
		_Configuration = config;
	}
	
	/*
	 * Start sending regular updates to the server.
	 * WARNING: Caller is responsible for connect()ing and close()ing client!
	 */
	public void start(ConnectionClient client) {
		if (!client.isReady()) 
			throw new RuntimeException("Must connect client before calling start()!");
		
		Calendar startTime = Calendar.getInstance(); //Record time start
		long timeElapsed = getTimeElapsed(startTime);
		while (timeElapsed < _Configuration.getTotalTime()) {
			List<Vessel> list = _Configuration.getVessels();
			try {
				for (int i=0; i < list.size(); i++) {
					UpdateData data = list.get(i).getUpdateData(Calendar.getInstance());
					client.sendUpdate(data);
				}
				Thread.sleep(_Configuration.getTimeInterval() * 1000);
				timeElapsed = getTimeElapsed(startTime);
			} catch (InterruptedException e) {
				break; //User pressed ctrl-c; we exit
			} catch (IOException e) {
				e.printStackTrace();
				// XXX TODO We need to decide what happens here!
				// If a transmission error occurs, print error and keep going
				// or do we print error and exit?
			}
		}
	}
	
	public long getTimeElapsed(Calendar start) {
		return Calendar.getInstance().getTimeInMillis() - start.getTimeInMillis() / 1000;
	}
}
