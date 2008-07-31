package tigase.pubsub.rest;

import java.io.IOException;
import java.net.InetSocketAddress;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

public class RestManager {

	public static void main(String[] args) throws IOException {
		HttpServer server = HttpServer.create(new InetSocketAddress(8000), 0);
		server.createContext("/applications/myapp", new HttpHandler() {

			@Override
			public void handle(HttpExchange exchange) throws IOException {
				System.out.println(exchange.getRequestURI());
				System.out.println("handle: " + exchange);
			}
		});
		server.setExecutor(null); // creates a default executor
		// server.start();
		System.out.println(".");
	}
}
