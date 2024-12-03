import java.io.*;
import java.net.*;
import java.sql.*;
import java.util.*;
import java.util.regex.*;
public class WebPageDownloader {
    private static final String DB_URL = "jdbc:mysql://localhost:3306/web_downloader1";
    private static final String DB_USER = "root";
    private static final String DB_PASSWORD = "";
    public static void main(String[] args) {
        Scanner scanner = new Scanner(System.in);
        System.out.print("Enter the URL to download: ");
        String inputUrl = scanner.nextLine();
        if (!isValidURL(inputUrl)) {
            System.out.println("Invalid URL. Exiting...");
            return;
        }
        try {
            Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD);
            long startTime = System.currentTimeMillis();
            String websiteName = new URL(inputUrl).getHost();
            String folderName = websiteName.replace(".", "_");
            File downloadFolder = new File(folderName);
            if (!downloadFolder.exists()) downloadFolder.mkdir();
            System.out.println("Downloading homepage...");
            String homepagePath = downloadPage(inputUrl, folderName, "index.html");
            long endTime = System.currentTimeMillis();
            long elapsedTime = endTime - startTime;
            PreparedStatement websiteStmt = conn.prepareStatement(
                    "INSERT INTO website (website_name, download_start_date_time, download_end_date_time, total_elapsed_time, total_downloaded_kilobytes) " +
                            "VALUES (?, NOW(), NOW(), ?, ?)", Statement.RETURN_GENERATED_KEYS);
            websiteStmt.setString(1, websiteName);
            websiteStmt.setLong(2, elapsedTime);
            websiteStmt.setFloat(3, getFileSizeInKB(new File(homepagePath)));
            websiteStmt.executeUpdate();
            ResultSet websiteRs = websiteStmt.getGeneratedKeys();
            int websiteId = 0;
            if (websiteRs.next()) {
                websiteId = websiteRs.getInt(1);
            }
            System.out.println("Extracting links...");
            List<String> links = extractLinksFromPage(homepagePath);
            for (String link : links) {
                System.out.println("Downloading: " + link);
                startTime = System.currentTimeMillis();
                try {
                    String fileName = "resource_" + UUID.randomUUID() + ".html";
                    downloadPage(link, folderName, fileName);
                    endTime = System.currentTimeMillis();
                    elapsedTime = endTime - startTime;
                    // Insert link record into database
                    PreparedStatement linkStmt = conn.prepareStatement(
                            "INSERT INTO link (link_name, website_id, total_elapsed_time, total_downloaded_kilobytes) VALUES (?, ?, ?, ?)");
                    linkStmt.setString(1, link);
                    linkStmt.setInt(2, websiteId);
                    linkStmt.setLong(3, elapsedTime);
                    linkStmt.setFloat(4, getFileSizeInKB(new File(folderName + "/" + fileName)));
                    linkStmt.executeUpdate();
                } catch (Exception e) {
                    System.out.println("Failed to download: " + link);
                }
            }
            System.out.println("Download completed.");
            conn.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    private static boolean isValidURL(String url) {
        try {
            new URL(url).toURI();
            return true;
        } catch (Exception e) {
            return false;
        }
    }
    private static String downloadPage(String urlStr, String folderName, String fileName) throws IOException {
        URL url = new URL(urlStr);
        BufferedReader reader = new BufferedReader(new InputStreamReader(url.openStream()));
        BufferedWriter writer = new BufferedWriter(new FileWriter(folderName + "/" + fileName));
        String line;
        while ((line = reader.readLine()) != null) {
            writer.write(line);
            writer.newLine();
        }
        reader.close();
        writer.close();
        return folderName + "/" + fileName;
    }
    private static List<String> extractLinksFromPage(String filePath) throws IOException {
        List<String> links = new ArrayList<>();
        String regex = "href=\"(http[^\"]*)\"";
        Pattern pattern = Pattern.compile(regex);
        BufferedReader reader = new BufferedReader(new FileReader(filePath));
        String line;
        while ((line = reader.readLine()) != null) {
            Matcher matcher = pattern.matcher(line);
            while (matcher.find()) {
                links.add(matcher.group(1));
            }
        }
        reader.close();
        return links;
    }
    private static float getFileSizeInKB(File file) {
        return file.length() / 1024f;
    }
}