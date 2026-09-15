import java.net.*;
import java.net.http.*;
import java.time.*;
import javax.net.ssl.*;
import java.util.concurrent.*;

class DeepSeekTransportProbe {
    public static void main(String[] args) throws Exception {
        try (var pool = Executors.newVirtualThreadPerTaskExecutor()) {
            for (boolean proxy : new boolean[]{false, true})
                for (boolean tls12 : new boolean[]{false, true})
                    pool.submit(() -> probe(proxy, tls12));
        }
    }
    static void probe(boolean proxy, boolean tls12) {
        for (int attempt = 1; attempt <= 3; attempt++) {
            String label = "proxy=" + proxy + " tls=" + (tls12 ? "1.2" : "default") + " attempt=" + attempt;
            try {
                var builder = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5));
                builder.proxy(proxy ? ProxySelector.of(InetSocketAddress.createUnresolved("127.0.0.1",7890)) : HttpClient.Builder.NO_PROXY);
                if (tls12) builder.sslParameters(new SSLParameters(null, new String[]{"TLSv1.2"}));
                try (var client = builder.build()) {
                    var response = client.send(HttpRequest.newBuilder(URI.create("https://api.deepseek.com/models"))
                            .timeout(Duration.ofSeconds(10)).GET().build(), HttpResponse.BodyHandlers.discarding());
                    System.out.println(label + " status=" + response.statusCode() + " " + response.version()
                            + " " + response.sslSession().map(SSLSession::getProtocol).orElse(""));
                }
            } catch (Exception e) {
                StringBuilder failure = new StringBuilder(label);
                for (Throwable cause=e; cause!=null; cause=cause.getCause())
                    failure.append(" -> ").append(cause.getClass().getSimpleName()).append(": ").append(cause.getMessage());
                System.out.println(failure);
            }
        }
    }
}
