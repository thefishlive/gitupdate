/**
 * gitupdater 0.1-SNAPSHOT
 * Copyright (C) 2013 James Fitzpatrick <james_fitzpatrick@outlook.com>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package io.github.thefishlive.updater;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.net.BindException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URL;
import java.security.KeyStore;

import org.apache.http.ConnectionClosedException;
import org.apache.http.HttpConnectionFactory;
import org.apache.http.HttpException;
import org.apache.http.HttpServerConnection;
import org.apache.http.impl.DefaultBHttpServerConnection;
import org.apache.http.impl.DefaultBHttpServerConnectionFactory;
import org.apache.http.protocol.BasicHttpContext;
import org.apache.http.protocol.HttpContext;
import org.apache.http.protocol.HttpProcessor;
import org.apache.http.protocol.HttpProcessorBuilder;
import org.apache.http.protocol.HttpService;
import org.apache.http.protocol.ResponseConnControl;
import org.apache.http.protocol.ResponseContent;
import org.apache.http.protocol.ResponseDate;
import org.apache.http.protocol.ResponseServer;
import org.apache.http.protocol.UriHttpRequestHandlerMapper;

import javax.net.ssl.KeyManager;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLServerSocketFactory;

public class HttpServer implements Runnable {

	public HttpServer() {
	}

	public void run() {
		try {
			int port = GitUpdater.port;

			// Set up the HTTP protocol processor
			HttpProcessor httpproc = HttpProcessorBuilder.create()
					.add(new ResponseDate())
					.add(new ResponseServer("GitUpdater/1.0-SNAPSHOT"))
					.add(new ResponseContent())
					.add(new ResponseConnControl()).build();

			// Set up request handlers
			UriHttpRequestHandlerMapper reqistry = new UriHttpRequestHandlerMapper();
			reqistry.register("*", new ResponceHandler());

			// Set up the HTTP service
			HttpService httpService = new HttpService(httpproc, reqistry);

			SSLServerSocketFactory sf = null;
			if (port == 8443) {
				// Initialize SSL context
				ClassLoader cl = getClass().getClassLoader();
				URL url = cl.getResource("my.keystore");
				if (url == null) {
					System.out.println("Keystore not found");
					System.exit(1);
				}
				KeyStore keystore = KeyStore.getInstance("jks");
				keystore.load(url.openStream(), "secret".toCharArray());
				KeyManagerFactory kmfactory = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
				kmfactory.init(keystore, "secret".toCharArray());
				KeyManager[] keymanagers = kmfactory.getKeyManagers();
				SSLContext sslcontext = SSLContext.getInstance("TLS");
				sslcontext.init(keymanagers, null, null);
				sf = sslcontext.getServerSocketFactory();
			}

			try {
				Thread t = new RequestListenerThread(port, httpService, sf);
				t.setDaemon(false);
				t.start();
			} catch (BindException ex) {
				System.out.println("Error binding to port " + port);
				System.out.println("Perhaps another server is running on that port");
				return;
			} catch (IOException ex) {
				ex.printStackTrace();
			}
		} catch (Exception ex) {
			ex.printStackTrace();
		}
	}

	static class RequestListenerThread extends Thread {

		private final HttpConnectionFactory<DefaultBHttpServerConnection> connFactory;
		private final ServerSocket serversocket;
		private final HttpService httpService;

		public RequestListenerThread(final int port, final HttpService httpService, final SSLServerSocketFactory sf) throws IOException {

			this.connFactory = DefaultBHttpServerConnectionFactory.INSTANCE;
			this.serversocket = sf != null ? sf.createServerSocket(port) : new ServerSocket(port);
			this.httpService = httpService;
		}

		@Override
		public void run() {
			System.out.println("Listening on port " + this.serversocket.getLocalPort());
			while (!Thread.interrupted()) {
				try {
					// Set up HTTP connection
					Socket socket = this.serversocket.accept();
					System.out.println("Incoming connection from " + socket.getInetAddress());
					HttpServerConnection conn = this.connFactory.createConnection(socket);

					// Start worker thread
					Thread t = new WorkerThread(this.httpService, conn);
					t.setDaemon(true);
					t.start();
				} catch (InterruptedIOException ex) {
					break;
				} catch (IOException e) {
					System.err.println("I/O error initialising connection thread: " + e.getMessage());
					break;
				}
			}
		}
	}

	static class WorkerThread extends Thread {

		private final HttpService httpservice;
		private final HttpServerConnection conn;

		public WorkerThread(final HttpService httpservice, final HttpServerConnection conn) {
			super();
			this.httpservice = httpservice;
			this.conn = conn;
		}

		@Override
		public void run() {
			HttpContext context = new BasicHttpContext(null);
			try {
				while (!Thread.interrupted() && this.conn.isOpen()) {
					this.httpservice.handleRequest(this.conn, context);
				}
			} catch (ConnectionClosedException ex) {
				System.err.println("Client closed connection");
			} catch (IOException ex) {
				System.err.println("I/O error: " + ex.getMessage());
			} catch (HttpException ex) {
				System.err.println("Unrecoverable HTTP protocol violation: " + ex.getMessage());
			} finally {
				try {
					this.conn.shutdown();
				} catch (IOException ignore) {
				}
			}
		}

	}
}
