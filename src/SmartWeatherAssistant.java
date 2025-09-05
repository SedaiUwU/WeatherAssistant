import java.io.IOException;
import java.io.PrintWriter;
import java.io.FileWriter;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Scanner;
import java.util.Scanner;

import org.json.JSONArray;
import org.json.JSONObject;

import static spark.Spark.*; // ✅ Spark for web routes

public class SmartWeatherAssistant {

    // ✅ Paste your OpenWeather API key here
    private static final String API_KEY = "25c7bb8f385ff2fbe09aaad95a9bea94";

    private static final String WEATHER_URL =
            "https://api.openweathermap.org/data/2.5/weather?q=%s&appid=%s&units=metric";
    private static final String AIR_URL =
            "https://api.openweathermap.org/data/2.5/air_pollution?lat=%s&lon=%s&appid=%s";

    private static final HttpClient HTTP = HttpClient.newHttpClient();
    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    public static void main(String[] args) {
    	int chosenPort = startServer(8080); // ✅ Try 8080, fallback to free port
        System.out.println("🚀 Server started at http://localhost:" + chosenPort);

        // ✅ Homepage
        get("/", (req, res) -> {
            return """
            <!DOCTYPE html>
            <html lang="en">
            <head>
                <meta charset="UTF-8">
                <title>Smart Weather Assistant</title>
                <style>
                    body {
                        font-family: 'Segoe UI', Tahoma, sans-serif;
                        background: linear-gradient(135deg, #74ebd5, #ACB6E5);
                        display: flex;
                        justify-content: center;
                        align-items: center;
                        height: 100vh;
                        margin: 0;
                        color: #333;
                    }
                    .card {
                        background: white;
                        padding: 2rem;
                        border-radius: 16px;
                        box-shadow: 0 4px 20px rgba(0,0,0,0.2);
                        text-align: center;
                        width: 350px;
                    }
                    h1 {
                        margin-bottom: 1rem;
                        color: #4a90e2;
                    }
                    input {
                        padding: 10px;
                        width: 70%;
                        border: 1px solid #ccc;
                        border-radius: 8px;
                        outline: none;
                        margin-bottom: 1rem;
                    }
                    button {
                        padding: 10px 20px;
                        border: none;
                        border-radius: 8px;
                        background: #4a90e2;
                        color: white;
                        cursor: pointer;
                        font-weight: bold;
                        transition: 0.3s;
                    }
                    button:hover {
                        background: #357ABD;
                    }
                    a {
                        display: inline-block;
                        margin-top: 1rem;
                        text-decoration: none;
                        color: #4a90e2;
                        font-weight: bold;
                    }
                </style>
            </head>
            <body>
                <div class="card">
                    <h1>🌤 Smart Weather Assistant</h1>
                    <form action='/weather'>
                        <input name='city' placeholder='Enter city'>
                        <br>
                        <button type='submit'>Get Weather</button>
                    </form>
                    <a href='/history'>📜 View History</a>
                </div>
            </body>
            </html>
            """;
        });



     // ✅ Weather Results
        get("/weather", (req, res) -> {
            String city = req.queryParams("city");
            if (city == null || city.isBlank()) {
                return "<p>❌ Please enter a valid city. <a href='/'>⬅ Back</a></p>";
            }

            JSONObject weather = fetchCurrentWeather(city);
            if (weather == null) {
                return "<p>❌ City not found or API error. <a href='/'>⬅ Back</a></p>";
            }

            String resolvedCity = weather.optString("name", city);
            JSONObject main = weather.getJSONObject("main");
            double temp = main.getDouble("temp");
            int humidity = main.optInt("humidity", -1);
            JSONArray weatherArr = weather.getJSONArray("weather");
            String description = weatherArr.length() > 0
                    ? weatherArr.getJSONObject(0).optString("description", "n/a")
                    : "n/a";
            double wind = weather.optJSONObject("wind") != null
                    ? weather.getJSONObject("wind").optDouble("speed", 0.0)
                    : 0.0;

            JSONObject coord = weather.getJSONObject("coord");
            double lat = coord.getDouble("lat");
            double lon = coord.getDouble("lon");

            Integer aqi = fetchAQI(lat, lon);
            String aqiText = aqiToText(aqi);

            StringBuilder tipsHtml = new StringBuilder("<ul>");
            for (String tip : recommendations(temp, description, aqi)) {
                tipsHtml.append("<li>").append(tip).append("</li>");
            }
            tipsHtml.append("</ul>");

            saveHistory(resolvedCity, temp, description, aqiText);

            return String.format("""
            <!DOCTYPE html>
            <html lang="en">
            <head>
                <meta charset="UTF-8">
                <title>%s - Weather</title>
                <style>
                    body {
                        font-family: 'Segoe UI', Tahoma, sans-serif;
                        background: linear-gradient(135deg, #74ebd5, #ACB6E5);
                        display: flex;
                        justify-content: center;
                        align-items: center;
                        height: 100vh;
                        margin: 0;
                        color: #333;
                    }
                    .card {
                        background: white;
                        padding: 2rem;
                        border-radius: 16px;
                        box-shadow: 0 4px 20px rgba(0,0,0,0.2);
                        text-align: center;
                        width: 400px;
                    }
                    h2 { color: #4a90e2; margin-bottom: 0.5rem; }
                    h3 { margin-top: 1rem; }
                    ul { text-align: left; }
                    a {
                        display: inline-block;
                        margin-top: 1rem;
                        text-decoration: none;
                        color: #4a90e2;
                        font-weight: bold;
                    }
                </style>
            </head>
            <body>
                <div class="card">
                    <h2>🌍 %s</h2>
                    <p>🌡 %.1f°C — %s</p>
                    <p>💧 Humidity: %d%%</p>
                    <p>🍃 Wind: %.1f m/s</p>
                    <p>🫁 Air Quality: %s</p>
                    <h3>🤖 Recommendations:</h3>
                    %s
                    <a href='/'>⬅ Back</a>
                </div>
            </body>
            </html>
            """, resolvedCity, resolvedCity, temp, capitalize(description), humidity, wind, aqiText, tipsHtml);
        });


     // ✅ History Page
        get("/history", (req, res) -> {
            try {
                java.nio.file.Path p = java.nio.file.Path.of("history.txt");
                if (!java.nio.file.Files.exists(p)) {
                    return """
                    <!DOCTYPE html>
                    <html lang="en">
                    <head>
                        <meta charset="UTF-8">
                        <title>History</title>
                        <style>
                            body {
                                font-family: 'Segoe UI', Tahoma, sans-serif;
                                background: linear-gradient(135deg, #74ebd5, #ACB6E5);
                                display: flex;
                                justify-content: center;
                                align-items: center;
                                height: 100vh;
                                margin: 0;
                                color: #333;
                            }
                            .card {
                                background: white;
                                padding: 2rem;
                                border-radius: 16px;
                                box-shadow: 0 4px 20px rgba(0,0,0,0.2);
                                text-align: center;
                                width: 400px;
                            }
                            h2 { color: #4a90e2; margin-bottom: 1rem; }
                            a {
                                display: inline-block;
                                margin-top: 1rem;
                                text-decoration: none;
                                color: #4a90e2;
                                font-weight: bold;
                            }
                        </style>
                    </head>
                    <body>
                        <div class="card">
                            <h2>📜 History</h2>
                            <p>No history yet.</p>
                            <a href='/'>⬅ Back</a>
                        </div>
                    </body>
                    </html>
                    """;
                }

                StringBuilder listItems = new StringBuilder();
                java.nio.file.Files.lines(p).forEach(line -> listItems.append("<li>").append(line).append("</li>"));

                return String.format("""
                <!DOCTYPE html>
                <html lang="en">
                <head>
                    <meta charset="UTF-8">
                    <title>History</title>
                    <style>
                        body {
                            font-family: 'Segoe UI', Tahoma, sans-serif;
                            background: linear-gradient(135deg, #74ebd5, #ACB6E5);
                            display: flex;
                            justify-content: center;
                            align-items: center;
                            height: 100vh;
                            margin: 0;
                            color: #333;
                        }
                        .card {
                            background: white;
                            padding: 2rem;
                            border-radius: 16px;
                            box-shadow: 0 4px 20px rgba(0,0,0,0.2);
                            width: 500px;
                        }
                        h2 { text-align: center; color: #4a90e2; }
                        ul { text-align: left; }
                        a {
                            display: inline-block;
                            margin-top: 1rem;
                            text-decoration: none;
                            color: #4a90e2;
                            font-weight: bold;
                        }
                    </style>
                </head>
                <body>
                    <div class="card">
                        <h2>📜 History</h2>
                        <ul>%s</ul>
                        <a href='/'>⬅ Back</a>
                    </div>
                </body>
                </html>
                """, listItems);
            } catch (IOException e) {
                return "⚠ Could not read history: " + e.getMessage();
            }
        });
    }

    private static int startServer(int preferredPort) {
        try (java.net.ServerSocket socket = new java.net.ServerSocket(preferredPort)) {
            // ✅ Port is free, close test socket and use it
            socket.close();
            port(preferredPort);
            init();
            awaitInitialization();
            return preferredPort;
        } catch (IOException e) {
            // ❌ Port is busy, fallback to random free port
            System.out.println("⚠ Port " + preferredPort + " busy, switching to a random free port.");
            port(0); // let Jetty pick
            init();
            awaitInitialization();
            return port(); // return the actual assigned port
        }
    }

    // ✅ Helper methods (same as your code)
    private static JSONObject fetchCurrentWeather(String city) throws IOException, InterruptedException {
        String url = String.format(WEATHER_URL,
                URLEncoder.encode(city, StandardCharsets.UTF_8),
                API_KEY);
        HttpRequest req = HttpRequest.newBuilder(URI.create(url)).GET().build();
        HttpResponse<String> res = HTTP.send(req, HttpResponse.BodyHandlers.ofString());
        if (res.statusCode() != 200) return null;
        return new JSONObject(res.body());
    }

    private static Integer fetchAQI(double lat, double lon) throws IOException, InterruptedException {
        String url = String.format(AIR_URL, String.valueOf(lat), String.valueOf(lon), API_KEY);
        HttpRequest req = HttpRequest.newBuilder(URI.create(url)).GET().build();
        HttpResponse<String> res = HTTP.send(req, HttpResponse.BodyHandlers.ofString());
        if (res.statusCode() != 200) return null;
        JSONObject obj = new JSONObject(res.body());
        JSONArray list = obj.optJSONArray("list");
        if (list == null || list.isEmpty()) return null;
        JSONObject main = list.getJSONObject(0).optJSONObject("main");
        return (main != null) ? main.optInt("aqi", -1) : null; // 1..5
    }

    private static String aqiToText(Integer aqi) {
        if (aqi == null || aqi < 1) return "Unknown";
        return switch (aqi) {
            case 1 -> "Good (AQI 1)";
            case 2 -> "Fair (AQI 2)";
            case 3 -> "Moderate (AQI 3)";
            case 4 -> "Poor (AQI 4)";
            case 5 -> "Very Poor (AQI 5)";
            default -> "Unknown";
        };
    }

    private static String[] recommendations(double tempC, String description, Integer aqi) {
        String desc = description.toLowerCase();
        StringBuilder tips = new StringBuilder();

        if (desc.contains("rain") || desc.contains("drizzle")) {
            tips.append("Umbrella or raincoat recommended.\n");
        }
        if (desc.contains("snow")) {
            tips.append("Wear warm layers and waterproof shoes.\n");
        }
        if (tempC >= 30) {
            tips.append("Stay hydrated, use sunscreen, avoid peak sun.\n");
        } else if (tempC <= 5) {
            tips.append("Wear a warm jacket, hat, and gloves.\n");
        } else {
            tips.append("Dress comfortably for mild weather.\n");
        }
        if (aqi != null && aqi >= 4) {
            tips.append("Air quality is poor—limit outdoor activity or wear a mask.\n");
        }

        return tips.toString().split("\n");
    }

    private static void saveHistory(String city, double temp, String description, String aqiText) {
        String line = String.format("%s | %s | %.1f°C | %s | AQI: %s%n",
                TS.format(LocalDateTime.now()), city, temp, capitalize(description), aqiText);
        try (PrintWriter out = new PrintWriter(new FileWriter("history.txt", true))) {
            out.print(line);
        } catch (IOException e) {
            System.out.println("⚠ Could not write history: " + e.getMessage());
        }
    }

    private static String capitalize(String s) {
        if (s == null || s.isBlank()) return s;
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }
}
