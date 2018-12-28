package edu.buffalo.cse.cse486586.simpledht;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.UnknownHostException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.ConcurrentModificationException;
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
 *      - PA1 code
 *      - https://www.cse.buffalo.edu/~eblanton/course/cse586/materials/2018S/development.pdf
 */

public class SimpleDhtProvider extends ContentProvider {

    static final String TAG = SimpleDhtProvider.class.getSimpleName();
    static final String REMOTE_PORT0 = "11108";
    static final String REMOTE_PORT1 = "11112";
    static final String REMOTE_PORT2 = "11116";
    static final String REMOTE_PORT3 = "11120";
    static final String REMOTE_PORT4 = "11124";
    private static final String KEY_FIELD = "key";
    private static final String VALUE_FIELD = "value";
    static final int SERVER_PORT = 10000;
    private static final String TABLE_NAME = "MessageStore";
    private final Uri mUri= buildUri("content", "edu.buffalo.cse.cse486586.simpledht.provider");
    String[] data = new String[2];
    static String Port;
    ArrayList<String> messagelist = new ArrayList<String>();
    ArrayList<String[]> query_list = new ArrayList<String[]>();
    ArrayList<String> query_temp_list = new ArrayList<String>();
    String [] datum;
    String[] datareceived;
    String[] datumreceived;
    String[] join;
    String[] query_result = null;
    String[] queryv_result = null;
    String query_datum;
    static ArrayList<Node_Message> node = new ArrayList<Node_Message>();
    boolean check = false;
    boolean checkresult;
    boolean checkinsert;
    boolean checkdelete;
    boolean checkquery;
    private DatabaseHelper DatabaseHelper;
    private SQLiteDatabase Database;
    String key;
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

    public String hash(String key)
    {
        String Hash ="";
        try {
            Hash = genHash(key);
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        }
        return Hash;
    }

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        // TODO Auto-generated method stub

        boolean dodelete=false;
        Database =DatabaseHelper.getWritableDatabase();

        if(!(node.size()==0 || node.size()==1)) {
            if(selection.equals("*")){
                new ClientTask().executeOnExecutor(AsyncTask.SERIAL_EXECUTOR, "delete_all");
            }
            else if(selection.equals("@")){
                Database.delete(TABLE_NAME, null,null);
            }
            else {
                String Hash_key = hash(selection);
                for (int index=0;index<node.size();index++) {
                    checkdelete = comparekeyvalues(index,Hash_key,dodelete);
                    if(checkdelete) {
                        if(!Port.equals(node.get(index).get_port_number())){
                            new ClientTask().executeOnExecutor(AsyncTask.SERIAL_EXECUTOR, "delete_selection", node.get(index).get_port_number(), selection);
                        }
                        else if(Port.equals(node.get(index).get_port_number())){
                            Database.delete(TABLE_NAME, KEY_FIELD + "='" + selection + "'", null);
                        }
                        break;
                    }
                }
            }
        }
        else if(node.size()==0 || node.size()==1) {
            boolean query_selection = selection.equals("@");
            boolean query_all = selection.equals("*");
            if (query_selection || query_all) {
                Database.delete(TABLE_NAME, null, null);
            } else if (!query_selection && !query_all) {
                Database.delete(TABLE_NAME, KEY_FIELD + "='" + selection + "'", null);
            }
        }
        return 0;
    }

    @Override
    public String getType(Uri uri) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        // TODO Auto-generated method stub

        boolean doinsert=false;
        Database = DatabaseHelper.getWritableDatabase();
        String key = values.getAsString(KEY_FIELD);
        String Hash_key = hash(key);

        if((node.size()==0 || node.size()==1)){
            checkresult = insertcheck(values);
            if (!checkresult) {
                Database.insert(TABLE_NAME, null, values);
            }
            else
            {
                Database.update(TABLE_NAME, values, KEY_FIELD + "='" + key + "'", null);
            }
        }
        else {
            for (int index=0;index<node.size();index++) {
                checkinsert = comparekeyvalues(index,Hash_key,doinsert);
                if(checkinsert) {
                    if (!Port.equals(node.get(index).get_port_number()))
                    {
                        String this_port_number = node.get(index).get_port_number();
                        String this_key = values.getAsString(KEY_FIELD);
                        String this_value = values.getAsString(VALUE_FIELD);
                        new ClientTask().executeOnExecutor(AsyncTask.SERIAL_EXECUTOR, "insert",this_port_number,this_key,this_value );
                    }
                    else if (Port.equals(node.get(index).get_port_number()))
                    {
                        checkresult = insertcheck(values);
                        if (!checkresult) {
                            Database.insert(TABLE_NAME, null, values);
                        }
                        else
                        {
                            Database.update(TABLE_NAME, values, KEY_FIELD + "='" + key + "'", null);
                        }
                    }
                    break;
                }

            }
        }
        return uri;
    }

    private SQLiteQueryBuilder builder() {
        SQLiteQueryBuilder queryBuilder = new SQLiteQueryBuilder();
        queryBuilder.setTables(TABLE_NAME);
        return queryBuilder;
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
    public boolean onCreate() {
        // TODO Auto-generated method stub

        DatabaseHelper = new DatabaseHelper(
                getContext()       // the application context
        );

        TelephonyManager tel = (TelephonyManager) getContext().getSystemService(Context.TELEPHONY_SERVICE);
        String portStr = tel.getLine1Number().substring(tel.getLine1Number().length() - 4);
        final String myPort = String.valueOf((Integer.parseInt(portStr) * 2));

        try {
            /*
             * Create a server socket as well as a thread (AsyncTask) that listens on the server
             * port.
             *
             * AsyncTask is a simplified thread construct that Android provides. Please make sure
             * you know how it works by reading
             * http://developer.android.com/reference/android/os/AsyncTask.html
             */
            ServerSocket serverSocket = new ServerSocket(SERVER_PORT);
            new ServerTask().executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, serverSocket);
        } catch (IOException e) {
            /*
             * Log is a good way to debug your code. LogCat prints out all the messages that
             * Log class writes.
             *
             * Please read http://developer.android.com/tools/debugging/debugging-projects.html
             * and http://developer.android.com/tools/debugging/debugging-log.html
             * for more information on debugging.
             */
            Log.e(TAG, "Can't create a ServerSocket");
            return false;
        }

        new ClientTask().executeOnExecutor(AsyncTask.SERIAL_EXECUTOR, "join", myPort);
        Port = myPort;
        return false;
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs,
                        String sortOrder) {
        // TODO Auto-generated method stub

        boolean doquery=false;
        Database=DatabaseHelper.getReadableDatabase();
        SQLiteQueryBuilder queryBuilder = builder();

        if((node.size()==0 || node.size()==1)){
            if(selection.equals("*") && selection.equals("@")) {
            } else if(!selection.equals("*") && !selection.equals("@")) {
                queryBuilder.appendWhere(KEY_FIELD + "='" + selection + "'");
            }
            Cursor query_data = queryBuilder.query(Database,projection,null,selectionArgs,null,null,null);;
            return  query_data;
        }
        else if(selection.equals("@"))
        {
            Cursor query_here = queryBuilder.query(Database,projection,null,selectionArgs,null,null,null);
            return query_here;
        }

        else if(!selection.equals("*")){
            queryBuilder.appendWhere(KEY_FIELD+"='"+selection+"'");
            String Hash_key = hash(selection);
            for (int index=0;index<node.size();index++) {
                checkquery = comparekeyvalues(index,Hash_key,doquery);
                if(checkquery) {
                    String port_here =node.get(index).get_port_number();
                    if (!Port.equals(port_here)) {
                        MatrixCursor cursor = queryvalue(index,selection);
                        return cursor;
                    }
                    else if (Port.equals(port_here)){
                        return queryBuilder.query(Database,projection,null,selectionArgs,null,null,null);
                    }
                }

            }
        }
        else if(selection.equals("*")){
            MatrixCursor cursor = queryvalues();
            return cursor;

        }

        return null;

    }

    private boolean insertcheck(ContentValues values) {
        SQLiteQueryBuilder queryBuilder = builder();
        Cursor cursor = queryBuilder.query(Database, null, null, null, null, null, null);
        while (cursor.moveToNext()==true) {
            key = cursor.getString(0);
            if (key.equals(values.getAsString(KEY_FIELD))) {
                check = true;
                break;
            }
        }
        cursor.close();
        return check;
    }

    private MatrixCursor queryvalues() {

        MatrixCursor cursor = new MatrixCursor(new String[] { KEY_FIELD, VALUE_FIELD });
        try {
            query_result = new ClientTask().executeOnExecutor(AsyncTask.SERIAL_EXECUTOR, "query_all").get();
        } catch (Exception e) {
            e.printStackTrace();
        }
        if (query_result == null) {
        }
        else if (query_result != null) {
            for(String query_value : query_result){
                String[] split_query = query_value.split(",");
                String key_here = split_query[0];
                String value_here = split_query[1];
                cursor.addRow(new Object[] {key_here , value_here});
            }
        }
        return cursor;
    }

    private MatrixCursor queryvalue(int i, String selection) {
        MatrixCursor cursor = new MatrixCursor(new String[] { KEY_FIELD, VALUE_FIELD });
        String port_num = node.get(i).get_port_number();
        try {
            queryv_result = new ClientTask().executeOnExecutor(AsyncTask.SERIAL_EXECUTOR, "query_selection", port_num, selection).get();
        } catch (Exception e) {
            e.printStackTrace();
        }
        if (queryv_result != null) {
            String key_here = queryv_result[0];
            String value_here = queryv_result[1];
            cursor.addRow(new Object[] {key_here , value_here });
        }
        return cursor;
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        // TODO Auto-generated method stub
        return 0;
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

    private class ServerTask extends AsyncTask<ServerSocket, String, Void> {

        @Override
        protected Void doInBackground(ServerSocket... sockets) {
            ServerSocket serverSocket = sockets[0];

            /*
             * TODO: Fill in your server code that receives messages and passes them
             * to onProgressUpdate().
             */

            while(true) {
                try {
                    Socket clientsocket = serverSocket.accept();
                    ObjectInputStream clientmessage = new ObjectInputStream(clientsocket.getInputStream());
                    String[] messagereceived = (String[]) clientmessage.readObject();
                    String messageType = messagereceived[0];

                    if (messageType.equals("join")) {
                        addnodes(messagereceived[1]);
                        Collections.sort(node, compare);
                        new ClientTask().executeOnExecutor(AsyncTask.SERIAL_EXECUTOR, "ring",REMOTE_PORT0);
                    }
                    else if(messageType.equals("ring")){
                        join = messagereceived[1].split(",");
                        if(Port.equals(REMOTE_PORT0)){
                        }
                        else if(!Port.equals(REMOTE_PORT0)) {
                            node.clear();
                            for (String join_node : join) {
                                addnodes(join_node);
                            }
                        }
                    }
                    else if(messageType.equals("insert")){
                        ContentValues values= new ContentValues();
                        values.put(KEY_FIELD,messagereceived[2]);
                        values.put(VALUE_FIELD,messagereceived[3]);
                        getContext().getContentResolver().insert(mUri, values);
                    }
                    else if(messageType.equals("delete_selection")){
                        getContext().getContentResolver().delete(mUri,messagereceived[2],null);
                    }
                    else if(messageType.equals("query_selection")){
                        datareceived = querymessage(messagereceived[2]);
                        SendData(clientsocket,datareceived);
                    }
                    else if(messageType.equals("delete_all")){
                        getContext().getContentResolver().delete(mUri,"@",null);
                    }
                    else if(messageType.equals("query_all")){
                        datumreceived = querymessages();
                        SendData(clientsocket,datumreceived);
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                } catch (ClassNotFoundException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    private void addnodes(String node_number) {
        String message_id = null;
        try {
            message_id = genHash(String.valueOf(Integer.parseInt(node_number) / 2));
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        }
        Node_Message node_message = new Node_Message(message_id,node_number);
        node.add(node_message);
    }

    private String[] querymessages() {
        Cursor cursor = getContext().getContentResolver().query(mUri, null,
                "@", null, null);
        while(cursor.moveToNext()==true){
            query_datum = cursor.getString(0) + "," + cursor.getString(1);
            messagelist.add(query_datum);
        }
        cursor.close();
        datum = messagelist.toArray(new String[messagelist.size()]);
        return datum;
    }

    private String[] querymessage(String key) {
        Cursor cursor = getContext().getContentResolver().query(mUri, null,
                key, null, null);
        while(cursor.moveToNext()==true){
            data[0]= cursor.getString(0);
            data[1] = cursor.getString(1);
        }
        cursor.close();
        return data;

    }

    private String[] clientData(Socket socket) {
        String[] messagereceivedfromserver = null;
        try {
            ObjectInputStream servermessage = new ObjectInputStream(socket.getInputStream());
            messagereceivedfromserver = (String[]) servermessage.readObject();
        } catch (IOException e) {
            e.printStackTrace();
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        }

        return messagereceivedfromserver;
    }

    private void SendData(Socket socket, String[] msgs) {
        try {
            ObjectOutputStream sendmessage = new ObjectOutputStream(socket.getOutputStream());
            sendmessage.writeObject(msgs);
            Thread.sleep(300);
        } catch (IOException e) {
            e.printStackTrace();
        } catch (InterruptedException e) {
            e.printStackTrace();
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
        String node_number = null;
        String[] msgToSend = new String[3];
        @Override
        protected String[] doInBackground(String... msgs) {
            String messageType = msgs[0];
            int node_size = node.size();
            try {
                if(messageType.equals("join")) {
                    Socket socket = new Socket(InetAddress.getByAddress(new byte[]{10, 0, 2, 2}),
                            Integer.parseInt(REMOTE_PORT0));
                    SendData(socket,msgs);
                    socket.close();
                }
                else if(messageType.equals("ring")){
                    for(Node_Message nodes: node){
                        if(node_number!=null)
                            node_number = node_number+","+nodes.get_port_number();
                        else if(node_number==null)
                            node_number = nodes.get_port_number();
                    }
                    msgToSend[0]=messageType;
                    msgToSend[1]=node_number;
                    for(Node_Message node_msg: node){
                        msgToSend[2]=node_msg.get_port_number();
                        Socket socket = new Socket(InetAddress.getByAddress(new byte[]{10, 0, 2, 2}),
                                Integer.parseInt(msgToSend[2]));
                        SendData(socket,msgToSend);
                        socket.close();
                    }
                }
                else if(messageType.equals("insert")){
                    String port_number_node = msgs[1];
                    Socket socket = new Socket(InetAddress.getByAddress(new byte[]{10, 0, 2, 2}),
                            Integer.parseInt(port_number_node));
                    SendData(socket,msgs);
                    socket.close();
                }
                else if(messageType.equals("delete_selection")){
                    String port_number_node = msgs[1];
                    Socket socket = new Socket(InetAddress.getByAddress(new byte[]{10, 0, 2, 2}),
                            Integer.parseInt(port_number_node));
                    SendData(socket,msgs);
                    socket.close();
                }
                else if(messageType.equals("query_selection")){
                    String port_number_node = msgs[1];
                    Socket socket = new Socket(InetAddress.getByAddress(new byte[]{10, 0, 2, 2}),
                            Integer.parseInt(port_number_node));
                    SendData(socket,msgs);
                    String[] messagereceivedfromserver = clientData(socket);
                    socket.close();
                    return messagereceivedfromserver;

                }
                else if(messageType.equals("delete_all")){
                    for(int i=0;i<node_size;i++) {
                        String node_port_num = node.get(i).get_port_number();
                        Socket socket = new Socket(InetAddress.getByAddress(new byte[]{10, 0, 2, 2}),
                                Integer.parseInt(node_port_num));
                        SendData(socket,msgs);
                        socket.close();

                    }
                }
                else if(messageType.equals("query_all")){
                    for(int i=0;i<node_size;i++) {
                        String node_port_num = node.get(i).get_port_number();
                        Socket socket = new Socket(InetAddress.getByAddress(new byte[]{10, 0, 2, 2}),
                                Integer.parseInt(node_port_num));
                        SendData(socket,msgs);
                        String[] messagereceivedfromserver = clientData(socket);
                        query_list.add(messagereceivedfromserver);
                        socket.close();
                    }
                    for(String[] list: query_list){
                        Collections.addAll(query_temp_list, list);
                    }
                    String[] queryall_result = new String[query_temp_list.size()];
                    return query_temp_list.toArray(queryall_result);
                }
            } catch (UnknownHostException e) {
                Log.e(TAG, "ClientTask UnknownHostException");
            } catch (IOException e) {
                Log.e(TAG, "ClientTask socket IOException");
            }

            return null;
        }
    }
}