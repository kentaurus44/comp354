package tests;

import static org.junit.Assert.*;

import java.util.Calendar;

import vms.*;

import org.junit.Test;

import common.Vessel;

public class VesselTest {

	@Test
	public void test() {
		String id = "myid";
		Vessel.VesselType type = Vessel.VesselType.BOAT;
		Coord startCoord = new Coord(0,0);
		Course course = new Course(5,5);
		Calendar startTime = Calendar.getInstance();
		Calendar curTime = (Calendar)startTime.clone();
		
		Vessel v = new Vessel(id, type);
		v.update(startCoord, course, startTime);
		
		assertEquals(id, v.getId());
		assertEquals(type, v.getType());
		
		try{
			assertEquals(startCoord, v.getCoord(startTime));
			assertEquals(course, v.getCourse(startTime));
		}catch(Exception e){
			System.out.println("Invalid timestamp");
		}
		
		assertEquals(startTime, v.getLastTimestamp());
		
		//NEVER allow snapshotting a time equal to last recorded time
		boolean success = false;
		try {
			v.update(new Coord(0,0), new Course(0,0), curTime);
		}
		catch (IllegalStateException e) {
			success = true;
		}
		finally {
			assertTrue(success);
		}
		
		//NEVER allow snapshotting a time lower than last recorded time
		success = false;
		try {
			curTime.add(Calendar.SECOND,  -2);
			v.update(new Coord(0,0), new Course(0,0), curTime);
		}
		catch (IllegalStateException e) {
			success = true;
		}
		finally {
			assertTrue(success);
			curTime.add(Calendar.SECOND, 2); //Set time back to what it was for next test
		}
		
		//Assume two seconds have passed; should have traveled exactly 10 meters on x and y axis
		curTime.add(Calendar.SECOND, 2);
		
		try{
			assertEquals(new Coord(10, 10), v.getCoord(curTime));
		}catch(Exception e){
			System.out.println("Invalid timestamp");
		}
		
		//Travel another 5 meters, then change course
		curTime.add(Calendar.SECOND, 1);
		Course newCourse = new Course(0, -10);
		
		try{
			v.update(v.getCoord(curTime), newCourse, curTime);
			assertEquals(newCourse, v.getCourse(curTime));
			assertEquals(new Coord(15, 15), v.getCoord(curTime));
		}catch(Exception e){
			System.out.println("Invalid timestamp");
		}
		
		assertEquals(curTime, v.getLastTimestamp());
		
		//Travel for two seconds, see if the new course was taken into account
		curTime.add(Calendar.SECOND, 2);
		
		try{
			assertEquals(new Coord(15, -5), v.getCoord(curTime));
		}catch(Exception e){
			System.out.println("Invalid timestamp");
		}
	}

}
