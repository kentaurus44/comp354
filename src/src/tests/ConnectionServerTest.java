package tests;

import static org.junit.Assert.*;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.Calendar;

import org.junit.*;

import vms.ConnectionServer;
import vms.Coord;
import vms.Course;
import vms.Vessel;
import vms.Vessel.VesselType;

public class ConnectionServerTest {
	
	static ConnectionServer cs = null;
	final Thread csThread = new Thread() {

		@Override
		public void run() {
			
			try {
				cs.bind(InetSocketAddress.createUnresolved("localhost", 11233));
				cs.start();
			} catch (IOException e) {
				fail(e.getMessage());
			}
		}
	};
	
	Calendar lastRefresh = null;
	Vessel lastVessel = null;
	
	public class CSObserver implements ConnectionServer.Observer {

		@Override
		public void update(String id, VesselType type, Coord newCoords,
				Course newCourse, Calendar timestamp) {
			lastVessel = new Vessel(id, type);
			lastVessel.update(newCoords, newCourse, timestamp);
		}

		@Override
		public void refresh(Calendar timestamp) {
			lastRefresh = (Calendar)timestamp.clone();
		}
		
	}
	
	@Before
	public void setUp() {
		cs = new ConnectionServer();
		cs.setMinimumRefresh(1000);
		cs.registerObserver(new CSObserver());
	}
	
	@After
	public void tearDown() throws IOException {
		cs.close();
		cs = null;
	}

	@Test
	public void testManualRefresh() throws IOException {
		assertNull(lastRefresh);
		Calendar timestamp = Calendar.getInstance();
		cs.refreshObservers(timestamp);
		assertEquals(timestamp, lastRefresh);
		timestamp = Calendar.getInstance();
		cs.refreshObservers(timestamp);
		assertEquals(timestamp, lastRefresh);
	}
	
	@Test
	public void testManualUpdate() throws IOException {
		assertNull(lastVessel);
		Calendar timestamp = Calendar.getInstance();
		cs.updateObservers("myid", VesselType.BOAT, new Coord(10,10), new Course(20,20), timestamp);
		assertEquals("myid", lastVessel.getId());
		assertEquals(VesselType.BOAT, lastVessel.getType());
		assertEquals(new Coord(10,10), lastVessel.getCoord(timestamp));
		assertEquals(new Course(20,20), lastVessel.getCourse(timestamp));
		assertEquals(timestamp, lastVessel.getLastTimestamp());
	}
	
	@Test
	public void testAutomaticRefresh() throws IOException, InterruptedException {
		csThread.start();
		Thread.sleep(2000);
		Calendar timestamp = lastRefresh;
		assertNotNull(timestamp);
		Thread.sleep(2000);
		assertTrue(timestamp.before(lastRefresh));
		cs.stop();
		timestamp = lastRefresh;
		Thread.sleep(2000);
		assertTrue(timestamp.equals(lastRefresh));
	}
	
	@Test
	public void testSendData() throws UnknownHostException, IOException, InterruptedException {
		csThread.start();
		//Wait for CS to bind socket...
		Thread.sleep(1000);
		Socket socket = new Socket();
		socket.connect(InetSocketAddress.createUnresolved("localhost", 11233));
		OutputStream stream = socket.getOutputStream();
		
		//Try sending garbage
		String msg = "gfenrwjgfe  fbd fbb fwwq";
		stream.write(msg.getBytes());
		Thread.sleep(500); //Allow for overhead of network
		assertNull(lastVessel); //Should NOT have called update()
		
		//Try sending netstring-encoded garbage
		msg = "gfenrwjgfe  fbd fbb fwwq";
		msg = msg.length() + ":" + msg; //Netstring encoding
		stream.write(msg.getBytes());
		Thread.sleep(500); //Allow for overhead of network
		assertNull(lastVessel); //Should NOT have called update()
		
		//Try sending bad vessel values
		msg = "{\"id\":[1,2,3],\"type\":\"WRONGENUMVALUE\",\"coords\":0,\"course\":{}}";
		msg = msg.length() + ":" + msg; //Netstring encoding
		stream.write(msg.getBytes());
		Thread.sleep(500); //Allow for overhead of network
		assertNull(lastVessel); //Should NOT have called update()
		
		//Try sending correct data
		msg = "{\"id\":\"myid\",\"type\":\"BOAT\",\"coords\":[10,10],\"course\":[20,-20]}";
		msg = msg.length() + ":" + msg; //Netstring encoding
		stream.write(msg.getBytes());
		Thread.sleep(500); //Allow for overhead of network
		assertNotNull(lastVessel);
		Calendar timestamp = lastVessel.getLastTimestamp();
		assertEquals("myid", lastVessel.getId());
		assertEquals(VesselType.BOAT, lastVessel.getType());
		assertEquals(new Coord(10,-10), lastVessel.getCoord(timestamp));
		assertEquals(new Course(20,-20), lastVessel.getCourse(timestamp));
		cs.stop();
		socket.close();
	}

}
