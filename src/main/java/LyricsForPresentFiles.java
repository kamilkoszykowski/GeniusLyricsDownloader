import org.jaudiotagger.audio.AudioFile;
import org.jaudiotagger.audio.AudioFileIO;
import org.jaudiotagger.audio.exceptions.CannotReadException;
import org.jaudiotagger.audio.exceptions.InvalidAudioFrameException;
import org.jaudiotagger.audio.exceptions.ReadOnlyFileException;
import org.jaudiotagger.tag.FieldKey;
import org.jaudiotagger.tag.Tag;
import org.jaudiotagger.tag.TagException;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;


import java.io.*;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.*;

public class LyricsForPresentFiles {
    public static void main(String[] args) throws InterruptedException {

        if(args.length != 1) {
            System.out.println("Błąd! Wprowadzono " + args.length + " zamiast 1 argumentu.");
            System.exit(0);
        }

        File mainPath = new File(args[0].replaceAll("\"", ""));

        if (mainPath.exists()) {

            LocalTime start = LocalTime.now();

            List<File> aufioFiles = extractAudioFilesNames(mainPath);

            System.out.println("Znaleziono " + aufioFiles.size() + " plików audio / " + aufioFiles.size() + " audio files was found");
            System.out.println("Pobieranie tekstów... / Downloading lyrics...");

            int numRunnables = 32;

            BlockingQueue<Runnable> queue = new ArrayBlockingQueue<Runnable>(numRunnables, true);
            RejectedExecutionHandler handler = new ThreadPoolExecutor.CallerRunsPolicy();
            ExecutorService executor = new ThreadPoolExecutor(numRunnables, numRunnables, 0L, TimeUnit.MILLISECONDS, queue, handler);

            for (File f : aufioFiles){
                executor.execute(new Runnable() {

                    @Override
                    public void run() {
                        saveLyricsTxt(f, downloadLyricsFromLink(linkFromFile(f)));
                    }
                });
            }

            executor.shutdown();
            while (executor.isTerminated() == false){
                Thread.sleep(50);
            }


            LocalTime end = LocalTime.now();
            long sec = ChronoUnit.SECONDS.between(start, end);

            System.out.println("Zakończono! / Completed!\nCzas pobierania plików wyniósł " + sec + " sekund. / The download time for the files was " + sec + " seconds.");
        } else {
            System.out.println("Błąd! Lokalizacja " + mainPath + " nie istnieje! / Error! Directory " + mainPath + " not exists!");
        }
    }


    static List<Integer> downloaderWorkNumbersArray(int songsPerDownloader, int downloaderNumber) {
        List<Integer> al = new ArrayList<>();
        for (int i = 1; i <= songsPerDownloader; i++) {
            al.add(downloaderNumber * i - 1);
        }
        return al;
    }

    static void downloadPartOfSongs(List<Integer> numbers, List<File> files) {
        for (int n : numbers) {
            saveLyricsTxt(files.get(n), downloadLyricsFromLink(linkFromFile(files.get(n))));
        }
    }

    static List<File> extractAudioFilesNames(File mainPath) {
        List<File> aufioFiles = new ArrayList<>();
        File[] files = mainPath.listFiles();
        for (File f : files) {
            if (f.isFile() && searchForAudioFiles(f)) {
                aufioFiles.add(f);
            }
            if (f.isDirectory()) {
                File[] sub1 = f.listFiles();
                for (File f1 : sub1) {
                    if (f1.isFile() && searchForAudioFiles(f1)) {
                        aufioFiles.add(f1);
                    }
                    if (f1.isDirectory()) {
                        File[] sub2 = f1.listFiles();
                        for (File f2 : sub2) {
                            if (f2.isFile() && searchForAudioFiles(f2)) {
                                aufioFiles.add(f2);
                            }
                            if (f2.isDirectory()) {
                                File[] sub3 = f2.listFiles();
                                for (File f3 : sub3) {
                                    if (f3.isFile() && searchForAudioFiles(f3)) {
                                        aufioFiles.add(f3);
                                    }
                                    if (f3.isDirectory()) {
                                        File[] sub4 = f3.listFiles();
                                        for (File f4 : sub4) {
                                            if (f4.isFile() && searchForAudioFiles(f4)) {
                                                aufioFiles.add(f4);
                                            }
                                            if (f4.isDirectory()) {
                                                File[] sub5 = f4.listFiles();
                                                for (File f5 : sub5) {
                                                    if (f5.isFile() && searchForAudioFiles(f5)) {
                                                        aufioFiles.add(f5);
                                                    }
                                                }
                                            }
                                        }

                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        return aufioFiles;
    }

    static boolean searchForAudioFiles(File f) {
        return f.toString().contains(".mp3") ||
                f.toString().contains(".wav");
    }

    static List<String> allLinksFromFiles(List<File> files) {
        List<String> allLinks = new ArrayList<>();
        for (File f : files) {
            allLinks.add(linkFromFile(f));
        }
        return allLinks;
    }

    static String linkFromFile(File file) {
        String title = "";
        String artist = "";
        String link = "";
        try {
            AudioFile f = AudioFileIO.read(file);
            Tag tag = f.getTag();
            if (tag.getFirst(FieldKey.TITLE) != null) {
                title = tag.getFirst(FieldKey.TITLE);
            }
            if (tag.getFirst(FieldKey.ARTIST) != null) {
                artist = tag.getFirst(FieldKey.ARTIST);
            }
            link = "https://genius.com/" + replacer(artist.toLowerCase()) + "-" + replacer(title.toLowerCase()) + "-lyrics";
            link = link.substring(0, 19) + link.substring(19, 20).toUpperCase() + link.substring(20);

            if (link.equals("https://genius.com/--lyrics")) {
                link = "https://genius.com/" + file.toString()
                        .replaceAll(".*\\\\", "")
                        .replaceAll(".mp3", "")
                        .replaceAll(".wav", "").replace(" - ", " ")
                        + "-lyrics";
                link = replacer(link.toLowerCase());
                link = link.substring(0, 19) + link.substring(19, 20).toUpperCase() + link.substring(20);
            }

        } catch (IOException | CannotReadException | ReadOnlyFileException | TagException | InvalidAudioFrameException e) {
            return null;
        }

        return link;
    }

    static String downloadLyricsFromLink(String URL) {
        try {
            Document document = Jsoup.connect(URL).get();
            String result = document.select("div[class^=\"lyrics\"]").toString();
            result = result.replaceAll("\\s+", " ")
                    .replaceAll("<br>", "\n")
                    .replaceAll("</a>", "")
                    .replaceAll("<a href.*?>", "")
                    .replaceAll("<.*?>", "")
            /*.replaceAll("\\[.*?\\]", "")*/;
            return result;
        } catch (IOException e) {
            return null;
        }
    }

    static String replacer(String filename) {
        return filename.replaceAll("\\s+\\(.*\\)", "")
                .replaceAll("ą", "")
                .replaceAll("ć", "")
                .replaceAll("ę", "")
                .replaceAll("ł", "")
                .replaceAll("ń", "")
                .replaceAll("ó", "")
                .replaceAll("ś", "")
                .replaceAll("ż", "")
                .replaceAll("ź", "")
                .replaceAll("&", "and")
                .replaceAll("'", "")
                .replaceAll(" ", "-");
    }

    static void saveLyricsTxt(File f, String output) {
        if (output != null) {
            try {
                //LOKALIZACJA ZAPISU PLIKÓW Z TEKSTAMI
                File dir = new File(f.getParent());
                File file = new File(f.toString().replaceAll(".mp3", "").replaceAll(".wav", "") + ".txt");
                if (!file.exists()) {
                    boolean mkdir = dir.mkdirs();
                }
                FileWriter writer = new FileWriter(file);
                writer.write(output);
                writer.close();
                System.out.println("ZAPISANO / SAVED " + file);
            } catch (IOException e) {
                System.out.println("Błąd! Zapisywanie tekstów zakończone niepowodzeniem. / Error! Saving lyrics ended with error.");
            }
        } else {
            System.out.println("Nie udało się pobrać i zapisać tekstu dla / Failed to download and save lyrics for : " + f);
        }
    }
}


