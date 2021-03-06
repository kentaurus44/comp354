package vms;

import java.net.*;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.util.*;
import java.io.Closeable;
import java.io.IOException;

import common.Netstring;
import common.Netstring.DecodeResult;
import common.UpdateData;

/*
 * Class: ConnectionServer
 * Listens to incoming connections and accepts them.
 * Expects radar data from clients. When receiving radar data
 * it will parse the data and notify all interested parties.
 */
public class ConnectionServer implements Closeable {
	public interface Observer {
		public void update(UpdateData data);
		public void refresh(Calendar timestamp);
	}
	
	private static long DEFAULT_REFRESH = 500; //Refresh every half second by default
	
	private boolean _Continue;
	private long _RefreshTime;
	private ByteBuffer _Buffer;
	private Selector _Selector;
	private ServerSocketChannel _Channel;
	private List<Observer> _Observers;
	
	public ConnectionServer () {
		_RefreshTime = DEFAULT_REFRESH;
		_Observers = new ArrayList<Observer>();
		_Buffer = ByteBuffer.allocate(1024*1024);
	}
	
	public void setMinimumRefresh(long milliseconds) {
		_RefreshTime = milliseconds;
	}
	
	public long getMinimumRefresh() {
		return _RefreshTime;
	}
	
	public void registerObserver(Observer o) {
		if (!_Observers.contains(o)) _Observers.add(o);
	}
	
	public void unregisterObserver(Observer o) {
		_Observers.remove(o); //remove() doesn't fail if object not found 
	}
	
	public void refreshObservers(Calendar timestamp) {
		for (int i=0; i < _Observers.size(); i++) {
			_Observers.get(i).refresh(timestamp);
		}
	}
	
	public void updateObservers(UpdateData data) {
		for (int i=0; i < _Observers.size(); i++) {
			_Observers.get(i).update(data);
		}
	}
	
	public void bind(SocketAddress addr) throws IOException {
		_Selector = Selector.open();
		_Channel = ServerSocketChannel.open();
		_Channel.configureBlocking(false);
		_Channel.socket().bind(addr);
		_Channel.register(_Selector, SelectionKey.OP_ACCEPT);
	}
	
	public void start() throws IOException {
		if (_Selector == null) throw new IOException("Must bind before calling start()!");
		
		_Continue = true;
		while (_Continue) {
			//Wait for an event from the network...
			int num = _Selector.select(_RefreshTime);
			if (num == 0) {
				// No event happened but select() returned, we reached a timeout
				refreshObservers(Calendar.getInstance());
				continue; //Go again as long as _Continue is true
			}
			//Something's on the wire! Let's see what it is...
			Set<SelectionKey> selectedKeys = _Selector.selectedKeys();
			Iterator<SelectionKey> it = selectedKeys.iterator();
			
			while (it.hasNext()) {
				SelectionKey key = it.next();
				it.remove();
				if (!key.isValid()) continue;
				if (key.isAcceptable()) {
					//We have a new connection to accept
					ServerSocketChannel ssc = (ServerSocketChannel)key.channel();
					_accept(ssc);
				}
				else if (key.isReadable()) {
					//We have data coming from one of the accepted connections
					_read(key);
				}
			}
		}
	}
	
	public void stop() {
		_Continue = false;
	}

	@Override
	public void close() throws IOException {
		if (_Channel != null) {
			_Channel.close();
			_Channel = null;
		}
		if (_Selector != null) {
			_Selector.close();
			_Selector = null;
		}
	}
	
	private void _accept(ServerSocketChannel ssc) throws IOException {
		SocketChannel sc = ssc.accept();
		sc.configureBlocking(false);
		//We are interested in data coming from this new connection
		sc.register(_Selector, SelectionKey.OP_READ);
	}
	
	private void _read(SelectionKey key) throws IOException {
		SocketChannel sc = (SocketChannel)key.channel();
		_Buffer.clear();

		int bytes;
		try {
			bytes = sc.read(_Buffer);
		} catch (IOException e) {
			//This is because the client forcibly disconnected
			key.cancel();
			sc.close();
			return;
		}
		if (bytes == -1) { //Normal disconnection
			sc.close();
			key.cancel();
			return;
		}
		//We have data! Ideally, at this point we should spin off into a new thread but for now this will do.
		UpdateData ud;
		DecodeResult res = new DecodeResult();
		byte[] buf_arr = _Buffer.array();
		while (true) {
			//As long as there is a netstring to read in the buffer, we try to read it
			try {
				res = Netstring.decode(buf_arr, res.end_pos, "UTF-8");
				if (res.data == null) {
					//No more valid netstrings in input
					//This is a potential bug;
					//We should save the remaining buffer on a per-client basis
					break;
				}
				ud = UpdateData.fromJSON(res.data);
			}
			catch (Exception e) {
				//ANYTHING wrong with the client data, we just ignore and move on
				return;
			}
			try {
				updateObservers(ud);
			}
			catch (IllegalStateException e) {
				//Client tried to send outdated data;
				//We can safely ignore this.
				return;
			}
		}
	}
}
