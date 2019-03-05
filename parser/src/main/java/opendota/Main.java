package opendota;

import com.google.gson.Gson;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import opendota.entity.ErrorMessage;
import opendota.entity.ReplayResponse;

import java.io.*;
import java.net.InetSocketAddress;
import java.util.concurrent.Executors;

public class Main {
    private static Gson g = new Gson();

    public static void main(String[] args) throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(Integer.valueOf(args.length > 0 ? args[0] : "5600")), 0);
        server.createContext("/", new MyHandler());
        server.setExecutor(Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors()));
        server.start();
    }

    static class MyHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange t) throws IOException {
            ReplayResponse replayResponse = new ReplayResponse();

            InputStream is = t.getRequestBody();
            OutputStream os = t.getResponseBody();

            t.sendResponseHeaders(200, 0);

            try {
                new Parse(is, replayResponse);
            } catch (Exception e) {
                replayResponse.getMatchEntries().clear();
                replayResponse.getHeroesOrder().clear();

                replayResponse.setErrorMessage(new ErrorMessage(e.getMessage()));
                e.printStackTrace();
            }

            os.write(g.toJson(replayResponse).getBytes());

            is.close();
            os.close();
        }
    }
}
