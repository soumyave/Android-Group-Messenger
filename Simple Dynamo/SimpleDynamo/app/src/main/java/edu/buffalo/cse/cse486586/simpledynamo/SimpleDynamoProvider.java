package edu.buffalo.cse.cse486586.simpledynamo;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;

import java.util.Collections;
import java.util.Comparator;
import java.util.Formatter;
import java.util.concurrent.ExecutionException;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteQueryBuilder;
import android.net.Uri;
import android.os.AsyncTask;
import android.telephony.TelephonyManager;
import android.util.Log;

/*
 *References used:
 *      - https://developer.android.com/guide/topics/providers/content-provider-creating.html
 *      - https://developer.android.com/reference/android/database/sqlite/SQLiteQueryBuilder.html
 *      - https://developer.android.com/reference/android/database/sqlite/SQLiteDatabase.html
 *      - PA1-3 code
 *      - https://www.cse.buffalo.edu/~eblanton/course/cse586/materials/2018S/development.pdf
 */

public class SimpleDynamoProvider extends ContentProvider {

	static final String TAG = SimpleDynamoProvider.class.getSimpleName();
	static final String REMOTE_PORT0 = "11108";
	static final String REMOTE_PORT1 = "11112";
	static final String REMOTE_PORT2 = "11116";
	static final String REMOTE_PORT3 = "11120";
	static final String REMOTE_PORT4 = "11124";
	static final String[] remoteport =  new String[]{ REMOTE_PORT0,REMOTE_PORT1,REMOTE_PORT2,REMOTE_PORT3,REMOTE_PORT4};
	private static final String KEY = "key";
	private static final String VALUE = "value";
	static final int SERVER_PORT = 10000;
	private static final String TABLE_NAME = "MessageStore";
	private final Uri mUri= buildUri("content", "edu.buffalo.cse.cse486586.simpledynamo.provider");
	static String Port;
	int predecessor_1 =0;
	int predecessor_2 =0;
	int successor = 0;
	int current = 0;
	static ArrayList<Node_Message> node = new ArrayList<Node_Message>();
	ArrayList<String[]> query_data = new ArrayList<String[]>();
	ArrayList<String[]> recover_data = new ArrayList<String[]>();
	ArrayList<String> temporary_store = new ArrayList<String>();
	private DatabaseHelper DatabaseHelper;
	private SQLiteDatabase Database;
	boolean checkinsert;
	boolean checkdelete;
	boolean checkquery;

	static Comparator<Node_Message> compare = new Comparator<Node_Message>() {
		public int compare(Node_Message message1, Node_Message message2) {
			int compare_message = message1.getId().compareTo(message2.getId());
			return compare_message;
		}
	};

	private Uri buildUri(String scheme, String authority) {
		Uri.Builder uriBuilder = new Uri.Builder();
		uriBuilder.authority(authority);
		uriBuilder.scheme(scheme);
		return uriBuilder.build();
	}

	private String genHash(String input) throws NoSuchAlgorithmException {
		MessageDigest sha1 = MessageDigest.getInstance("SHA-1");
		byte[] sha1Hash = sha1.digest(input.getBytes());
		Formatter formatter = new Formatter();
		for (byte b : sha1Hash) {
			formatter.format("%02x", b);
		}
		return formatter.toString();
	}

	private class Node_Message {
		String message_id;
		String port_number;
		public Node_Message(String message_id, String port_number) {
			this.message_id = message_id;
			this.port_number = port_number;
		}
		public String getId() {
			return message_id;
		}
		public String get_port_number() {
			return port_number;
		}
	}

	private String hash(String key) {
		String Hash = "";
		try {
			Hash = genHash(key);
		} catch (NoSuchAlgorithmException e) {
			e.printStackTrace();
		}
		return Hash;
	}

	{
		if(node.size()>=0 && node.size() < 5){
			String value,node_id;
			node.clear();
			Node_Message node_message;
			for (String port_number : remoteport) {
				value = String.valueOf(Integer.parseInt(port_number) / 2);
				node_id = hash(value);
				node_message = new Node_Message(node_id, port_number);
				node.add(node_message);
			}
			Collections.sort(node, compare);
		}
	}

	private void timer() {
		try {
			Thread.sleep(100);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
	}

	@Override
	public int delete(Uri uri, String selection, String[] selectionArgs) {
		// TODO Auto-generated method stub
		boolean dodelete=false;
		Database =DatabaseHelper.getWritableDatabase();

		if(selection.equals("*")) {
			new ClientTask().executeOnExecutor(AsyncTask.SERIAL_EXECUTOR, "delete_all");
		}
		else if(selection.equals("@"))
		{
			Database.delete(TABLE_NAME, null, null);
		}
		else {
			String Hash_key = hash(selection);
			for (int index=0;index<node.size();index++) {
				checkdelete = comparekeyvalues(index,Hash_key,dodelete);
				if(checkdelete) {
					new ClientTask().executeOnExecutor(AsyncTask.SERIAL_EXECUTOR, "delete_selection", node.get(index).get_port_number(), selection);
					replication(selection," ",index,"delete_replica");
					break;
				}
			}
		}
		return 0;
	}

	@Override
	public String getType(Uri uri) {
		// TODO Auto-generated method stub
		return null;
	}

	private boolean comparekeyvalues(int index, String Hash_key,boolean docheck) {
		String current_node = node.get(index).getId();
		String last_node = node.get(node.size()-1).getId();
		if(index==0){
			if(Hash_key.compareTo(current_node) <= 0 || Hash_key.compareTo(last_node)>0) {
				docheck =true;
			}
		}
		else if(index!=0)
		{
			String previous_node = node.get(index-1).getId();
			if (Hash_key.compareTo(current_node) <= 0 && Hash_key.compareTo(previous_node)>0){
				docheck=true;
			}
		}
		return docheck;
	}

	@Override
	public Uri insert(Uri uri, ContentValues values) {
		// TODO Auto-generated method stub
		timer();
		boolean doinsert=false;
		Database = DatabaseHelper.getWritableDatabase();
		String Hash_key = hash(values.getAsString(KEY));
		for (int index=0;index<node.size();index++) {
			checkinsert = comparekeyvalues(index,Hash_key,doinsert);
			if(checkinsert) {
				String key = values.getAsString(KEY);
				String value = values.getAsString(VALUE);
				new ClientTask().executeOnExecutor(AsyncTask.SERIAL_EXECUTOR, "insert", node.get(index).get_port_number(), key,value);
				replication(key,value,index,"replicate");
				break;
			}
		}
		return uri;
	}

	private void insert_replicate(boolean update, String key_here, String value_here) {
		String update_value="";
		ContentValues values = store_data(key_here,value_here);
		String key = values.getAsString(KEY);
		Cursor cursor = builder(key);
		while (cursor.moveToNext()) {
			update_value = cursor.getString(1);
			update = true;
		}
		if (!update) {
			insert_db(values);
		} else if(update){
			update_db(values,update_value);
		}
		cursor.close();
	}

	@Override
	public boolean onCreate() {
		// TODO Auto-generated method stub
		String[] recovery_data;
		DatabaseHelper = new DatabaseHelper(
				getContext()       // the application context
		);

		TelephonyManager tel = (TelephonyManager) getContext().getSystemService(Context.TELEPHONY_SERVICE);
		String portStr = tel.getLine1Number().substring(tel.getLine1Number().length() - 4);
		final String myPort = String.valueOf((Integer.parseInt(portStr) * 2));

		try {
			ServerSocket serverSocket = new ServerSocket(SERVER_PORT);
			new ServerTask().executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, serverSocket);
		} catch (IOException e) {
			Log.e(TAG, "Can't create a ServerSocket");
		}
		Port = myPort;
		recovery_data = call_client("recover",myPort);

		if (recovery_data != null) {
			String key_here,value_here;
			String[] data_to_store;
			for(String recover_d : recovery_data){
				boolean doupdate = false;
				String existing_value="";
				String[] update_value,current_value;
				int update_value_version ,current_value_version;
				data_to_store = recover_d.split(",");
				key_here = data_to_store[0];
				value_here = data_to_store[1]+","+ data_to_store[2];
				ContentValues values = store_data(key_here,value_here);
				String key = values.getAsString(KEY);
				Cursor cursor = builder(key);
				while (cursor.moveToNext()) {
					existing_value = cursor.getString(1);
					doupdate = true;
				}
				cursor.close();
				if(!doupdate) {
					Database.insert(TABLE_NAME, null, values);
				}
				if (doupdate) {
					String value_to_update = values.getAsString(VALUE);
					update_value = value_to_update.split(",");
					current_value = existing_value.split(",");
					update_value_version = Integer.parseInt(update_value[1]);
					current_value_version = Integer.parseInt(current_value[1]);
					if(update_value_version>=current_value_version){
						Database.update(TABLE_NAME, values, KEY + "='" + key + "'", null);
					}

				}
			}
		}
		return false;
	}

	private Cursor builder(String key_here) {
		Database =DatabaseHelper.getWritableDatabase();
		SQLiteQueryBuilder queryBuilder = new SQLiteQueryBuilder();
		queryBuilder.setTables(TABLE_NAME);
		if(!key_here.equals(" "))
			queryBuilder.appendWhere(KEY+"='"+key_here+"'");
		Cursor cursor = queryBuilder.query(Database, null, null, null, null, null, null);
		return cursor;
	}

	@Override
	public Cursor query(Uri uri, String[] projection, String selection,
						String[] selectionArgs, String sortOrder) {
		// TODO Auto-generated method stub
		boolean doquery=false;
		Database=DatabaseHelper.getReadableDatabase();
		if(node.size() < 5){
			timer();
		}
		SQLiteQueryBuilder queryBuilder= new SQLiteQueryBuilder();
		queryBuilder.setTables(TABLE_NAME);

		if(selection.equals("@"))
		{
			MatrixCursor matrixCursor;
			Cursor cursor;
			String key ;
			String[] value;
			matrixCursor = new MatrixCursor(new String[]{KEY, VALUE});
			cursor =  queryBuilder.query(Database,projection,null,selectionArgs,null,null,null);
			while (cursor.moveToNext()){
				value = cursor.getString(1).split(",");
				key = cursor.getString(0);
				matrixCursor.addRow(new Object[]{key, value[0]});
			}
			return matrixCursor;
		}
		else if(selection.equals("*")){
			String[] query_all_data;
			query_all_data = call_client("query_all", " ");
			MatrixCursor cursor = new MatrixCursor(new String[] { KEY, VALUE });
			if (query_all_data != null) {
				String[] queried_data;
				String key,value;
				for(String query_d : query_all_data){
					queried_data = query_d.split(",");
					key = queried_data[0];
					value = queried_data[1];
					cursor.addRow(new Object[] { key, value});
				}
			}
			return cursor;
		}
		else if(!selection.equals("*")){
			queryBuilder.appendWhere(KEY+"='"+selection+"'");
			String Hash_key=hash(selection);
			for (int index=0;index<node.size();index++) {
				checkquery = comparekeyvalues(index,Hash_key,doquery);
				if(checkquery) {
					String[] query_data;
					int new_value_version=0,count = 0;
					MatrixCursor matrixCursor = new MatrixCursor(new String[]{KEY, VALUE});
					String queried_value[] = null,query_replica[] = null;
					String node_number;
					while (count == 0){
                        node_number = node.get(index).get_port_number();
                        query_data = query_client(node_number,selection,"query_selection");
                        if (query_data != null && query_data[1] != null) {
                            queried_value = query_data[1].split(",");
                            count++;
                        }
                        int replica_node,neighbours;
                        for (neighbours = index + 1; neighbours <= index + 2; neighbours++) {
                            replica_node = neighbours;
                            int size = node.size();
                            if (size<=neighbours) {
                                replica_node = neighbours - size;
                            }
                            node_number = node.get(replica_node).get_port_number();
                            query_data = query_client(node_number,selection,"query_selection");
                            if (query_data != null && query_data[1]!=null) {
                                if(count == 0){
                                    queried_value = query_data[1].split(",");
                                    count++;
                                }
                                else{
                                    String[] value_version = query_data[1].split(",");
                                    int query_value_version = Integer.parseInt(value_version[1]);
                                    if( query_value_version>= new_value_version) {
                                        query_replica = query_data[1].split(",");
                                        new_value_version = Integer.parseInt(query_replica[1]);
                                        count++;
                                    }
                                }
                            }
                        }
                    }
					if(Integer.parseInt(queried_value[1]) < Integer.parseInt(query_replica[1])){
                        matrixCursor.addRow(new Object[]{selection, query_replica[0]});
                    }
                    else if(Integer.parseInt(query_replica[1]) <= Integer.parseInt(queried_value[1])||Integer.parseInt(query_replica[1]) == 0 ){
                        matrixCursor.addRow(new Object[]{selection, queried_value[0]});
                    }
					return matrixCursor;
				}

			}
		}

		return null;
	}

	private String[] query_client(String node_number, String selection, String type) {
		String[] result = null;
		try {
			result = new ClientTask().executeOnExecutor(AsyncTask.SERIAL_EXECUTOR, type, node_number, selection).get();
		} catch (InterruptedException e) {
			e.printStackTrace();
		} catch (ExecutionException e) {
			e.printStackTrace();
		}
		return result;
	}

	private void replication(String param1, String param2, int index, String type) {
		int replica_node;
		for(int j=index+1;j<=index+2;j++){
			replica_node = j;
			if(j>=node.size()){
				replica_node = j-node.size();
			}
			if(param2.equals(" "))
				new ClientTask().executeOnExecutor(AsyncTask.SERIAL_EXECUTOR, type, node.get(replica_node).get_port_number(), param1);
			else
				new ClientTask().executeOnExecutor(AsyncTask.SERIAL_EXECUTOR, type, node.get(replica_node).get_port_number(), param1, param2);
		}
	}

	private String[] call_client(String type, String port) {
		String[] result = null;
		try {
			if (port != " ")
				result = new ClientTask().executeOnExecutor(AsyncTask.SERIAL_EXECUTOR, "recover", port).get();
			else
				result = new ClientTask().executeOnExecutor(AsyncTask.SERIAL_EXECUTOR, type).get();
		} catch (InterruptedException e) {
			e.printStackTrace();
		} catch (ExecutionException e) {
			e.printStackTrace();
		}
		return result;
	}

	@Override
	public int update(Uri uri, ContentValues values, String selection,
					  String[] selectionArgs) {
		// TODO Auto-generated method stub
		return 0;
	}

	private class ServerTask extends AsyncTask<ServerSocket, String, Void> {

		@Override
		protected Void doInBackground(ServerSocket... sockets) {
			ServerSocket serverSocket = sockets[0];
			while(true) {
				try {
					ObjectInputStream clientmessage;
					Socket server = serverSocket.accept();
					clientmessage = new ObjectInputStream(server.getInputStream());
					String[] messsagereceivedfromclient = (String[]) clientmessage.readObject();
					String type = messsagereceivedfromclient[0];
					if(type.equals("insert")){
						String key_here = messsagereceivedfromclient[2];
						String value_here = messsagereceivedfromclient[3];
						boolean update = false;
						insert_replicate(update,key_here,value_here);
					}
					else if(type.equals("replicate")){
						String key_here = messsagereceivedfromclient[2];
						String value_here = messsagereceivedfromclient[3];
						boolean update = false;
						insert_replicate(update,key_here,value_here);
					}
					else if(type.equals("query_selection")){
						Database=DatabaseHelper.getReadableDatabase();
						String key_here = messsagereceivedfromclient[2];
						String[] query_selection_data = new String[2];
						Cursor cursor = builder(key_here);
						int index;
						while(cursor.moveToNext()){
							for(index = 0;index<=1;index++)
								query_selection_data[index]= cursor.getString(index);
						}
						Server_send(server,query_selection_data);
						cursor.close();
					}
					else if(type.equals("delete_selection")){
						String key = messsagereceivedfromclient[2];
						delete_db(key);
					}
					else if(type.equals("query_all")){
						ArrayList<String> query_data_list = new ArrayList<String>();
						Cursor cursor = getContext().getContentResolver().query(mUri, null,
								"@", null, null);
						while(cursor.moveToNext()){
							store(cursor,query_data_list);
						}
						cursor.close();
						String [] query_all_data = query_data_list.toArray(new String[query_data_list.size()]);
						Server_send(server,query_all_data);
					}
					else if(type.equals("delete_all")){
						getContext().getContentResolver().delete(mUri,"@",null);
					}
					else if(type.equals("delete_replica")){
						String key = messsagereceivedfromclient[2];
						delete_db(key);
					}
					else if(type.equals("recover")){
						if(messsagereceivedfromclient[2].equals("predecessor")){
							Integer[]  predecessors;
							ArrayList<String> predecessor_recovery_list = new ArrayList<String>();
							int predecessor_node_1 = Integer.parseInt(messsagereceivedfromclient[3]);
							int predecessor_node_2 = Integer.parseInt(messsagereceivedfromclient[4]);
							Cursor cursor = builder(" ");
							predecessors = new Integer[2];
							predecessors[0] = predecessor_node_1;
							predecessors[1] = predecessor_node_2;
							while(cursor.moveToNext()){
								boolean doinsert = false;
								String Hash_key = hash(cursor.getString(0));
								for(Integer predecessor : predecessors){
									boolean insert = comparekeyvalues(predecessor,Hash_key,doinsert);
									if(insert){
										store(cursor,predecessor_recovery_list);
										break;
									}
								}
							}
							cursor.close();
							String [] recovery_data = predecessor_recovery_list.toArray(new String[predecessor_recovery_list.size()]);
							Server_send(server,recovery_data);
						}
						else if(messsagereceivedfromclient[2].equals("successor")){
							int successor_node;
							ArrayList<String> successor_recovery_list = new ArrayList<String>();
							Cursor cursor = builder(" ");
							successor_node = Integer.parseInt(messsagereceivedfromclient[3]);
							while(cursor.moveToNext()){
								boolean doinsert = false;
								String Hash_key = hash(cursor.getString(0));
								boolean insert = comparekeyvalues(successor_node,Hash_key,doinsert);
								if(insert){
									store(cursor,successor_recovery_list);
								}
							}
							cursor.close();
							String [] recovery_data = successor_recovery_list.toArray(new String[successor_recovery_list.size()]);
							Server_send(server,recovery_data);
						}
					}
				} catch (ClassNotFoundException e) {
					e.printStackTrace();
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		}
	}

	private void store(Cursor cursor, ArrayList<String> list) {
		String result = cursor.getString(0) + "," + cursor.getString(1);
		list.add(result);
	}

	private ContentValues store_data(String key_here, String value_here) {
		ContentValues values= new ContentValues();
		values.put("key", key_here);
		values.put("value",value_here);
		return values;
	}

	private String[] add_to_list(ArrayList<String[]> query_data) {
		for(String[] q_data: query_data){
			Collections.addAll(temporary_store, q_data);
		}
		return temporary_store.toArray(new String[temporary_store.size()]);
	}

	private void insert_db(ContentValues values) {
		String version,insert_value;
		version = "1";
		String value = values.getAsString(VALUE);
		insert_value = value+","+version;
		values.put("value",insert_value);
		Database.insert(TABLE_NAME, null, values);
	}

	private void update_db(ContentValues values, String value_existing) {
		String new_version,new_value;
		int current_value_version;
		String[] current_value = value_existing.split(",");
		String key = values.getAsString(KEY);
		String value = values.getAsString(VALUE);
		current_value_version = Integer.parseInt(current_value[1]);
		current_value_version++;
		new_version = String.valueOf(current_value_version);
		new_value = value+","+new_version;
		values.put(VALUE,new_value);
		Database.update(TABLE_NAME, values, KEY + "='" + key + "'", null);
	}

	private void delete_db(String key) {
		Database = DatabaseHelper.getWritableDatabase();
		Database.delete(TABLE_NAME, KEY + "='" + key + "'", null);
	}

	private void predecessor_successor(int node_index) {
		if (node.get(node_index).get_port_number().equals(Port)) {
			int size = node.size();
			if(node_index!=0 && node_index!=1)
			{
				predecessor_1 = node_index-1;
				predecessor_2 = node_index-2;
			}
			else  if(node_index==1){
				predecessor_1 = node_index - 1;
				predecessor_2 = size -1;
			}
			else if (node_index == 0) {
				predecessor_1 = size - 1;
				predecessor_2 = size - 2;
			}
			if (node_index != size - 1)
				successor =node_index+1;
			else
				successor = 0;
			current =node_index;
		}
	}
	/***
	 * ClientTask is an AsyncTask that should send a string over the network.
	 * It is created by ClientTask.executeOnExecutor() call whenever OnKeyListener.onKey() detects
	 * an enter key press event.
	 *
	 * @author stevko
	 *
	 */

	private class ClientTask extends AsyncTask<String, Void, String[]> {

		@Override
		protected String[] doInBackground(String... msgs) {
			String type = msgs[0];

			if(type.equals("insert")){
				ClientSendData(msgs);
			}
			else if(type.equals("replicate")){
				ClientSendData(msgs);
			}
			else if(type.equals("query_selection")){
				Socket socket ;
				ObjectOutputStream clientmessage;
				ObjectInputStream servermessage;
				String[] result = new String[0];
				try {
					String port = msgs[1];
					socket = new Socket(InetAddress.getByAddress(new byte[]{10, 0, 2, 2}),
							Integer.parseInt(port));
					clientmessage = new ObjectOutputStream(socket.getOutputStream());
					clientmessage.writeObject(msgs);
					servermessage = new ObjectInputStream(socket.getInputStream());
					result = (String[]) servermessage.readObject();
					if(socket!=null){
						socket.close();}
				} catch (IOException e) {
					e.printStackTrace();
					return null;
				} catch (ClassNotFoundException e) {
					e.printStackTrace();
				}
				return result;

			}
			else if(type.equals("query_all")){
				for(int index=0;index<node.size();index++) {
					ClientSendReceive(index,msgs,query_data);
				}
				String[] data_list = add_to_list(query_data);
				return data_list;
			}
			else if(type.equals("delete_all")){
				for(String port : remoteport) {
					ClientSendData(msgs);
				}
			}
			else if(type.equals("delete_selection")){
				ClientSendData(msgs);
			}
			else if(type.equals("delete_replica")){
				ClientSendData(msgs);
			}
			else if(type.equals("recover")){
				String[] message = new String[5];
				for(int index=0;index<node.size();index++) {
					predecessor_successor(index);
				}
				message[2] = "predecessor";
				message[0] = msgs[0];
				message[1] = msgs[1];
				message[3] = String.valueOf(predecessor_1);
				message[4] = String.valueOf(predecessor_2);
				ClientSendReceive(predecessor_1,message, recover_data);
				message[2] = "successor";
				message[3] = String.valueOf(current);
				ClientSendReceive(successor,message, recover_data);
				String[] data_list = add_to_list(query_data);
				return data_list;

			}
			return null;
		}
	}

	private void Server_send(Socket server, String[] result) {
		ObjectOutputStream servermessage;
		try {
			servermessage = new ObjectOutputStream(server.getOutputStream());
			servermessage.writeObject(result);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	private void ClientSendReceive(int node_index, String[] msgs, ArrayList<String[]> query_data) {

		Socket clientsocket;
		ObjectOutputStream clientmessage = null;
		ObjectInputStream servermessage = null;
		try {
			String port = node.get(node_index).get_port_number();
			clientsocket = new Socket(InetAddress.getByAddress(new byte[]{10, 0, 2, 2}),
					Integer.parseInt(port));
			if (clientsocket != null) {
				clientmessage = new ObjectOutputStream(clientsocket.getOutputStream());
			}
			if (clientmessage != null) {
				clientmessage.writeObject(msgs);
			}
			if (clientsocket != null) {
				servermessage = new ObjectInputStream(clientsocket.getInputStream());
			}
			if (servermessage != null) {
				this.query_data.add((String[]) servermessage.readObject());
			}
			if (clientsocket != null) {
				clientsocket.close();
			}
		} catch (IOException e) {
			e.printStackTrace();
		} catch (ClassNotFoundException e) {
			e.printStackTrace();
		}
	}

	private void ClientSendData(String[] msgs) {
		Socket clientsocket;
		ObjectOutputStream clientmessage = null;
		try {
			String port = msgs[1];
			clientsocket = new Socket(InetAddress.getByAddress(new byte[]{10, 0, 2, 2}),
					Integer.parseInt(port));
			if (clientsocket != null) {
				clientmessage = new ObjectOutputStream(clientsocket.getOutputStream());
			}
			if (clientmessage != null) {
				clientmessage.writeObject(msgs);
			}
			timer();
			if (clientsocket != null) {
				clientsocket.close();
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
}
