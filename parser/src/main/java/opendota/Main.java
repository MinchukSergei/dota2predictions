package opendota;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.io.*;
import java.net.InetSocketAddress;
import java.util.concurrent.Executors;

public class Main {

    public static void main(String[] args) throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(Integer.valueOf(args.length > 0 ? args[0] : "5600")), 0);
        server.createContext("/", new MyHandler());
        server.setExecutor(Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors()));
        server.start();
    }

    static class MyHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange t) throws IOException {
            t.sendResponseHeaders(200, 0);

            InputStream is = t.getRequestBody();
            OutputStream os = t.getResponseBody();

            try {
                new Parse(is, os);
            } catch (Exception e) {
                e.printStackTrace();
            }

            os.close();
        }
    }
}
