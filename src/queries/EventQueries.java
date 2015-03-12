package queries;

import java.sql.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;

import database.DBConnect;
import models.*;
import queries.CalendarQueries;

public class EventQueries {
	
	public static void checkUpdateCounts(int[] updateCounts) {
		for (int i = 0; i < updateCounts.length; i++) {
			if (updateCounts[i] >= 0) {
				System.out.println("Successfully executed; updateCount=" + updateCounts[i]);
			} else if (updateCounts[i] == Statement.SUCCESS_NO_INFO) {
				System.out.println("Successfully executed; updateCount=Statement.SUCCESS_NO_INFO");
			} else if (updateCounts[i] == Statement.EXECUTE_FAILED) {
				System.out.println("Failed to execute; updateCount=Statement.EXECUTE_FAILED");
			}
		}
	}

	/**
	 * Get all the evens from an ArrayList of calendars.
	 * @param cal
	 */
	public static ArrayList<Event> getEvents(ArrayList<Calendar> cal, UserGroup ug){
		Connection conn = null;
		PreparedStatement pstmt = null;
		ArrayList<Event> events = new ArrayList<>();
		if (cal.size() > 0){
			try {
				conn = DBConnect.getConnection();
				conn.setAutoCommit(false);
				String query = "SELECT DISTINCT * FROM Calendar NATURAL JOIN CalendarEvent NATURAL JOIN Event NATURAL JOIN Attends WHERE ";
				for (int i = 0; i< cal.size(); i++) {
					if (i != 0){
						query += "OR ";
					}
					query = query + "(CalendarID = ? AND UserGroupID = ? )";
				}
				pstmt = conn.prepareStatement(query);
				for (int i = 0; i/2 < cal.size(); i+=2) {
					pstmt.setInt(i+1, cal.get(i/2).getCalendarID());
					pstmt.setInt(i+2, ug.getUserGroupID());
				}

				ResultSet result = pstmt.executeQuery();
				while (result.next()) {
					int eventID = result.getInt("EventID");
					int calendarID = result.getInt("CalendarID");
					String calendarName = result.getString("CalendarName");
					String eventName = result.getString("EventName");
					String eventNote = result.getString("EventNote");
					DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.S");
					System.out.println(result.getTimestamp("From").toString());
					LocalDateTime from = LocalDateTime.parse(result.getTimestamp("From").toString(), formatter);
					LocalDateTime to = LocalDateTime.parse(result.getTimestamp("To").toString(), formatter);
					int attends = result.getInt("Attends");

					ArrayList<Calendar> templist = new ArrayList<>();
					templist.add(new Calendar(calendarID, null, null));

					Calendar calendar = new Calendar (calendarID, calendarName, null);
					events.add(new Event(eventID, eventName, eventNote, null, from, to, calendar, attends));
				}
				result.close();
				pstmt.close();
				conn.close();
			} catch (Exception e){
				System.out.println(e);
			}
			return events;
		}else{
			System.out.println("The parameter contains no calendars");
			return null;
		}
	}

	/**
	 * Creates an Event with the given name.
	 * @param users
	 */

	public static Event createEvent(Event event){
		Connection con = null;
		PreparedStatement prep;
		try{
			con = DBConnect.getConnection();
			con.setAutoCommit(false);

			String query = "INSERT INTO `Event`(`EventName`, `EventNote`, `From`, `To`) VALUES (?, ?, ?, ?);";
			prep = con.prepareStatement(query, Statement.RETURN_GENERATED_KEYS);
			prep.setString(1, event.getName());
			prep.setString(2, event.getNote());
			prep.setString(3, event.getFrom().toString());
			prep.setString(4, event.getTo().toString());
			prep.executeUpdate();
			ResultSet keys = prep.getGeneratedKeys();
			keys.next();
			int key = keys.getInt(1);
			event.setEventID(key);

			query = "INSERT INTO CalendarEvent(CalendarID, EventID) SELECT ?, LAST_INSERT_ID();";
			prep = con.prepareStatement(query);
			prep.setInt(1, event.getCal().getCalendarID());
			prep.executeUpdate();
			
			query = "INSERT INTO Attends(UserGroupID, EventID, Attends) VALUES (?,?,?);";
			prep = con.prepareStatement(query);
			for (UserGroup ug : event.getParticipants()){
				prep.setInt(1, ug.getUserGroupID());
				prep.setInt(2, event.getEventID());
				prep.setInt(3, 0);
				prep.addBatch();
			}
			int[] updateCounts = prep.executeBatch();
			checkUpdateCounts(updateCounts);
			
			con.commit();

			prep.close();
			con.close();

			return event;
		} catch(SQLException e){
			System.out.println(e);
			return null;
		}
	}
	
	/**
	 * Delete an event.
	 * @param event
	 */
	public static void deleteEvent(Event event){
		Connection con = null;
		PreparedStatement prep;
		try{
			con = DBConnect.getConnection();
			String query = "DELETE FROM Event WHERE EventID = ?";
			prep = con.prepareStatement(query);
			prep.setInt(1, event.getEventID());
			System.out.println(prep.toString());
			prep.execute();
			System.out.println("Executed");
			prep.close();
			con.close();
		} catch(SQLException e){
			System.out.println(e);
		}
	}

	/**
	 * Update attendants.
	 * @param event
	 */
	public static void updateAttends(Event event, Attendant attendant){

		/* status: 0 = no response, 1 = Attends, 2 = Not attending*/
		Connection con = null;
		PreparedStatement prep;
		try{
			con = DBConnect.getConnection();
			String query = "UPDATE `Attends` "
					+ "SET `Attends` = ? "
					+ "WHERE `EventID` = ? AND `UserGroupID` = ?";
			prep = con.prepareStatement(query);
			prep.setInt(1, attendant.getStatus());
			prep.setInt(2, event.getEventID());
			prep.setInt(3, attendant.getUserGroupID());
			System.out.println(prep.toString());
			prep.execute();
			UserGroup ug = new UserGroup(attendant.getUserGroupID(), attendant.getName(), null);
			ArrayList<Calendar> cals = CalendarQueries.getCalendars(ug);
			int calID = -1;
			for (Calendar cal : cals){
				if (cal.getName().equals(attendant.getName())){
					calID = cal.getCalendarID();
					break;
				}
			}
			if (calID != -1){
				query = "INSERT INTO `CalendarEvent`(`CalendarID`, `EventID`) VALUES (?,?) ;";
				prep = con.prepareStatement(query);
				prep.setInt(1, calID);
				prep.setInt(2, event.getEventID());
				prep.execute();
			}
			prep.close();
			con.close();
		} catch(SQLException e){
			System.out.println(e);
		}
	}
	
	/**
	 * Delete an event.
	 * @param event
	 */
	public static void setAttends(Event event, ArrayList<UserGroup> attendant){
		/* status: 0 = no response, 1 = Attends, 2 = Not attending*/
		Connection con = null;
		PreparedStatement prep;
		try{
			con = DBConnect.getConnection();
			con.setAutoCommit(false);
			String query = "INSERT INTO Attends(UserGroupID, EventID, Attends) VALUES (?,?,?);";
			prep = con.prepareStatement(query);
			for (UserGroup ug : attendant){
				prep.setInt(1, ug.getUserGroupID());
				prep.setInt(2, event.getEventID());
				prep.setInt(3, 0);
				prep.addBatch();
			}
			int[] updateCounts = prep.executeBatch();
			checkUpdateCounts(updateCounts);
			con.commit();
			prep.close();
			con.close();
		} catch(SQLException e){
			System.out.println(e);
		}
	}

	public static ArrayList<Attendant> getAttendants (Event event){

		ArrayList<Attendant> attends = new ArrayList<Attendant>();
		Connection con = null;
		PreparedStatement prep;
		try{
			con = DBConnect.getConnection();
			String query = "SELECT * FROM Attends NATURAL JOIN UserGroup WHERE EventID = ? ;";
			prep = con.prepareStatement(query);
			prep.setInt(1, event.getEventID());
			ResultSet rs = prep.executeQuery();
			while (rs.next()) {
				int userGroupID = rs.getInt("UserGroupID");
				String name = rs.getString("GroupName");
				int attends_status = rs.getInt("Attends");
				attends.add(new Attendant(userGroupID, name, attends_status));
			}
			rs.close();
			prep.close();
			con.close();
		} catch(SQLException e){
			System.out.println(e);
		}
		return attends;
	}
	
	/**
	 * Edit an event that already exists.
	 * @param event
	 */
	public static void editEvent(Event event, UserGroup sender){
		Connection con = null;
		PreparedStatement prep;
		try{
			con = DBConnect.getConnection();
			String query = "UPDATE `Event` "
					+ "SET `EventName` = ?, "
					+ "`EventNote` = ?, "
					+ "`From` = ?, "
					+ "`To` = ? "
					+ "WHERE `EventID` = ?";
			prep = con.prepareStatement(query);
			prep.setString(1, event.getName());
			prep.setString(2, event.getNote());
			prep.setString(3, event.getFrom().toString());
			prep.setString(4, event.getTo().toString());
			prep.setInt(5, event.getEventID());
			System.out.println(prep.toString());
			prep.executeUpdate();
			
			ArrayList<UserGroup> new_participants = new ArrayList<>();
			ArrayList<UserGroup> temp_new_participants = event.getParticipants();
			ArrayList<Attendant> attendants = getAttendants(event);
			for (UserGroup u : temp_new_participants){
				for (int x = 0; x < attendants.size(); x++){
					if (u.getUserGroupID() == attendants.get(x).getUserGroupID()){
						new_participants.add(u);
					}
				}
			}
			for (UserGroup u : new_participants){
				temp_new_participants.remove(u);
			}
			
			new_participants = temp_new_participants;
			System.out.println(new_participants);

			query = "INSERT INTO Attends(UserGroupID, EventID, Attends) VALUES (?,?,?);";
			prep = con.prepareStatement(query);
			for (UserGroup ug : new_participants){
				prep.setInt(1, ug.getUserGroupID());
				prep.setInt(2, event.getEventID());
				prep.setInt(3, 0);
				prep.addBatch();
			}
			prep.executeBatch();
			
			query = "INSERT INTO Notification(EventID, Note, UserGroupID, IsInvite) VALUES (?,?,?,?);";
			prep = con.prepareStatement(query, Statement.RETURN_GENERATED_KEYS);
			for (UserGroup ug : new_participants){
				prep.setInt(1, event.getEventID());
				prep.setString(2, event.getNote());
				prep.setInt(3, sender.getUserGroupID());
				prep.setInt(4, 1);
				prep.addBatch();
			}
			ArrayList<Integer> auto_keys = new ArrayList<>();
			prep.executeBatch();
			ResultSet keys = prep.getGeneratedKeys();
			while(keys.next()){
				auto_keys.add(keys.getInt(1));
			}
			System.out.println(auto_keys);
			
			query = "INSERT INTO HasRead(UserGroupID, NoteID, HasRead) VALUES (?,?,?);";
			prep = con.prepareStatement(query);
			int counter = 0;
			for (UserGroup ug : event.getParticipants()){
				prep.setInt(1, ug.getUserGroupID());
				prep.setInt(2, auto_keys.get(counter));
				prep.setInt(3, 0);
				prep.addBatch();
				counter += 1;
			}
			prep.executeBatch();
			
			System.out.println("Executed");
			con.commit();
			prep.close();
			con.close();
		} catch(SQLException e){
			System.out.println(e);
		}
	}
	
	
	
	public static void main(String[] args) {

	//
	//	Calendar cal = new Calendar(3, "Yolo", null);
	//	Event ev = new Event(22, "Slå ned Sigurd", null, LocalDateTime.parse("2015-03-03T05:39:00"), LocalDateTime.parse("2015-03-03T05:41:00"), cal);
//
	//	createEvent(ev);
	//
	//
	//	deleteEvent(ev);
	//	editEvent(ev);
//
	//	UserGroup ug = new UserGroup(5, "Fellesprosjekt", null);
	//	ArrayList<Calendar> cal3 = CalendarQueries.getCalendars(ug);
	//	ArrayList<Event> events = getEvents(cal3);
	//	for (Event event : events){
	//		System.out.println(event.toString());
	//	}
	//	Event e = new Event(1, null, null, null, null, null, null);
	//	Person p = new Person(null, null, 1);
	//	updateAttends(e, p, 1);
	//
	//
	//}
}}
