import java.io.*;
import java.net.MalformedURLException;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.net.URLConnection;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;

/**
 * This class deals with downloading the file.
 */
class FileDownloader implements Runnable {
    private static String dir;
    private static String fileName;
    private static String link;
    private static long totalSize;
    private static URL url;

    /**
     * This is a constructor to initialise values of link, fileName and dir variables.
     * @param link Link to the file that the user wants to download
     * @param fileName Filename of the file that the user wants to save as after it is downloaded
     * @param dir Directory in which the file needs to be saved.
     */
    public FileDownloader(String link, String fileName, String dir){
        FileDownloader.link = link;
        FileDownloader.fileName = fileName;
        FileDownloader.dir = dir;
    }

    /**
     * This function is used to get the value of dir variable.
     * @return The directory in which the user wants to save the file.
     */
    public static String getDir(){
        return dir;
    }

    /**
     * This is the overridden run method of the Runnable interface and deals with the main part of opening connections and downloading the file.
     */
    @Override
    public void run() {
        link = link.replace('\\', '/');
        if (!(link.startsWith("http://") || link.startsWith("https://"))){
            link = "http://" + link;
        }
        if (link.startsWith("https://github.com/") || (link.startsWith("http://github.com/"))){
            if (!(link.endsWith("?raw=true"))){
                link = link + "?raw=true";
            }
        }
        try {
            // If link is of an YouTube video, then the following block of code will execute.
            if (Drifty_CLI.isYoutubeLink(link)) {
                try {
                    downloadFromYouTube("");
                } catch (IOException e) {
                    try {
                        System.out.println("Getting ready to download the file...");
                        Drifty_CLI.logger.log("INFO", "Getting ready to download the file...");
                        copyYt_dlp cy = new copyYt_dlp();
                        cy.copyToTemp();
                        try {
                            downloadFromYouTube(copyYt_dlp.tempDir);
                        } catch (InterruptedException ie) {
                            System.out.println("User interrupted while downloading the file!");
                            Drifty_CLI.logger.log("ERROR", "User interrupted while downloading the file! " + ie.getMessage());
                        } catch (IOException io1){
                            System.out.println("Failed to download YouTube video!");
                            Drifty_CLI.logger.log("ERROR", "Failed to download YouTube video! " + io1.getMessage());
                        }
                    } catch (IOException io){
                        System.out.println("Failed to initialise YouTube video downloader!");
                        Drifty_CLI.logger.log("ERROR", "Failed to initialise YouTube video downloader! " + io.getMessage());
                    }
                } catch (InterruptedException e) {
                    System.out.println("User interrupted while downloading the file!");
                    Drifty_CLI.logger.log("ERROR", "User interrupted while downloading the file! " + e.getMessage());
                }
            }
            else {
                url = new URL(link);
                URLConnection openConnection = url.openConnection();
                openConnection.connect();
                totalSize = openConnection.getContentLength();
                if (fileName.length() == 0) {
                    String[] webPaths = url.getFile().trim().split("/");
                    fileName = webPaths[webPaths.length-1];
                }
                dir = dir.replace('/', '\\');
                if (dir.length() != 0) {
                    if (dir.equals(".\\\\") || dir.equals(".\\")) {
                        dir = "";
                    }
                } else {
                    System.out.println("Invalid Directory Entered !");
                    Drifty_CLI.logger.log("ERROR", "Invalid Directory Entered !");
                }
                try {
                    new CheckDirectory(dir);
                } catch (IOException e){
                    System.out.println("Failed to create the directory : " + dir + " ! " + e.getMessage());
                    Drifty_CLI.logger.log("ERROR", "Failed to create the directory : " + dir + " ! " + e.getMessage());
                }
                downloadFile();
            }
        } catch (MalformedURLException e) {
            System.out.println("Invalid Link!");
            Drifty_CLI.logger.log("ERROR", "Invalid Link! " + e.getMessage());
        } catch (SocketTimeoutException e){
            System.out.println("Timed out while connecting to " + url + " !");
            Drifty_CLI.logger.log("ERROR", "Timed out while connecting to " + url + " ! " + e.getMessage());
        } catch (IOException e) {
            System.out.println("Failed to connect to " + url + " !");
            Drifty_CLI.logger.log("ERROR", "Failed to connect to " + url + " ! " + e.getMessage());
        }
    }

    /**
     * This method deals with downloading the file.
     */
    private static void downloadFile(){
        ReadableByteChannel readableByteChannel;
        try {
            InputStream urlStream = url.openStream();
            System.out.println();
            readableByteChannel = Channels.newChannel(urlStream);
            try {
                FileOutputStream fos = new FileOutputStream(dir + fileName);
                ProgressBarThread progressBarThread = new ProgressBarThread(fos, totalSize, fileName);
                progressBarThread.start();
                Drifty_CLI.logger.log("INFO", "Downloading " + fileName + " ...");
                fos.getChannel().transferFrom(readableByteChannel, 0, Long.MAX_VALUE);
                progressBarThread.setDownloading(false);
                // keep main thread from closing the IO for short amt. of time so UI thread can finish and output
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException ignored) {}

            } catch (SecurityException e) {
                System.out.println("Write access to " + dir + fileName + " denied !");
                Drifty_CLI.logger.log("ERROR", "Write access to " + dir + fileName + " denied ! " + e.getMessage());
            }catch (IOException e) {
                System.out.println("Failed to download the contents ! ");
                Drifty_CLI.logger.log("ERROR", "Failed to download the contents ! " + e.getMessage());
            }
        } catch (NullPointerException e){
            System.out.println("Failed to get I/O operations channel to read from the data stream !");
            Drifty_CLI.logger.log("ERROR", "Failed to get I/O operations channel to read from the data stream !" + e.getMessage());
        } catch (IOException e) {
            System.out.println("Failed to get a data stream !");
            Drifty_CLI.logger.log("ERROR", "Failed to get a data stream ! " + e.getMessage());
        }
        if (dir.length() == 0){
            dir = System.getProperty("user.dir");
        }
        if (!(dir.endsWith("\\"))) {
            dir = dir + System.getProperty("file.separator");
        }
    }

    /**
     * This method deals with downloading videos from YouTube in mp4 format.
     * @param dirOfYt_dlp The directory of yt-dlp file. Default - "". If Drifty is run from its jar file, this argument will have the directory where yt-dlp has been extracted to.
     * @throws InterruptedException When the I/O operation is interrupted using keyboard or such type of inputs.
     * @throws IOException When an I/O problem appears while downloading the YouTube video.
     */
    private static void downloadFromYouTube(String dirOfYt_dlp) throws InterruptedException, IOException {
        System.out.println("Trying to download the file ...");
        Drifty_CLI.logger.log("INFO", "Trying to download the file ...");
        ProcessBuilder processBuilder = new ProcessBuilder(dirOfYt_dlp + "yt-dlp", "--quiet", "--progress", "-P", dir, link);
        processBuilder.inheritIO();
        Process yt_dlp = processBuilder.start();
        yt_dlp.waitFor();
        int exitValueOfYt_Dlp = yt_dlp.exitValue();
        if (exitValueOfYt_Dlp == 0){
            System.out.println("Successfully downloaded the file!");
            Drifty_CLI.logger.log("INFO", "Successfully downloaded the file!");
        } else if (exitValueOfYt_Dlp == 1) {
            System.out.println("Failed to download the file!");
            Drifty_CLI.logger.log("INFO", "Failed to download the file!");
        }
    }
}
