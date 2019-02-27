package opendota;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.*;

public class Main {

    public static void main(String[] args) throws Exception {
//        HttpServer server = HttpServer.create(new InetSocketAddress(Integer.valueOf(args.length > 0 ? args[0] : "5600")), 0);
//        server.createContext("/", new MyHandler());
//        server.setExecutor(java.util.concurrent.Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors()));
//        server.start();

//        Map jsonObj = new Gson().fromJson(new JsonReader(new FileReader("H:/Minchuk/replays_data.json")), Map.class);
        parseReplays("H:/Minchuk/replays/decompressed/", "H:/Minchuk/parsed_replays/");
    }

    private static void parseReplays(String sourceFolder, String ouputFolder) {
        File rootFolder = new File(sourceFolder);
        File processedInput = new File(sourceFolder + File.separator + "processed");
        File failedInput = new File(processedInput.getAbsolutePath() + File.separator + "failed");
        File parsedFailedOutput = new File(ouputFolder + File.separator + "failed");
        File errorsFileLog = new File(ouputFolder + File.separator + "error.log");

        if (!processedInput.exists()) {
            processedInput.mkdir();
        }

        if (!failedInput.exists()) {
            failedInput.mkdir();
        }

        if (!parsedFailedOutput.exists()) {
            parsedFailedOutput.mkdir();
        }

        File[] decompressedFiles = rootFolder.listFiles((dir, name) -> name.toLowerCase().endsWith(".dem"));

        try (PrintWriter pr = new PrintWriter(new BufferedWriter(new FileWriter(errorsFileLog, true)))) {
            for (File file : decompressedFiles) {
                File parsedFile = new File(ouputFolder + File.separator + file.getName() + ".txt");
                boolean successful = false;
                try (InputStream is = new FileInputStream(file);
                     OutputStream os = new FileOutputStream(parsedFile)
                ) {
                    new Parse(is, os);
                    successful = true;
                } catch (IOException e) {
                    pr.println(String.format("Error during parse %s: %s", file.getName(), e.getMessage()));
                    file.renameTo(new File(failedInput + File.separator + file.getName()));
                    parsedFile.renameTo(new File(parsedFailedOutput + File.separator + parsedFile.getName()));
                }

                if (successful) {
                    file.renameTo(new File(processedInput.getAbsolutePath() + File.separator + file.getName()));
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    static class MyHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange t) throws IOException {
            t.sendResponseHeaders(200, 0);
            InputStream is = t.getRequestBody();
            OutputStream os = new FileOutputStream("./output.txt");
            try {
                new Parse(is, os);
            } catch (Exception e) {
                e.printStackTrace();
            }
            os.close();
            OutputStream os2 = t.getResponseBody();
            os2.close();
        }
    }
}
