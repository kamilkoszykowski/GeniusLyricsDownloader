import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.*;

public class ArtistAllLyrics {
    public static void main(String[] args) throws InterruptedException {

        if(args.length != 3) {
            System.out.println("Błąd! Wprowadzono " + args.length + " zamiast 3 argumentów.");
            System.exit(0);
        }

        List<String> albumLinks = getAlbumLinks(args);

        if (albumLinks != null) {

            LocalTime start = LocalTime.now();

            Counter counter = new Counter();

            System.out.println("Znaleziono " + albumLinks.size() + " linków do albumów / " + albumLinks.size() + " album links found");
            System.out.println("Pobieranie albumów... / Downloading albums...\n");

            List<String> songLinks = new ArrayList<>();

            int numRunnables = 64;

            BlockingQueue<Runnable> queue = new ArrayBlockingQueue<Runnable>(numRunnables, true);
            RejectedExecutionHandler handler = new ThreadPoolExecutor.CallerRunsPolicy();
            ExecutorService executor = new ThreadPoolExecutor(numRunnables, numRunnables, 0L, TimeUnit.MILLISECONDS, queue, handler);

            for (String a : albumLinks) {
                executor.execute(new Runnable() {

                    @Override
                    public void run() {
                        songLinks.addAll(albumLyricsDownloader(a));
                    }
                });
            }

            executor.shutdown();
            while (executor.isTerminated() == false){
                Thread.sleep(50);
            }


            BlockingQueue<Runnable> queue1 = new ArrayBlockingQueue<Runnable>(numRunnables, true);
            RejectedExecutionHandler handler1 = new ThreadPoolExecutor.CallerRunsPolicy();
            ExecutorService executor1 = new ThreadPoolExecutor(numRunnables, numRunnables, 0L, TimeUnit.MILLISECONDS, queue1, handler1);

            for (String s : songLinks){
                executor1.execute(new Runnable() {

                    @Override
                    public void run() {
                        lyricsDownloader(s, counter, args);
                    }
                });
            }

            executor1.shutdown();
            while (executor1.isTerminated() == false){
                Thread.sleep(50);
            }

            megaStatsWriter(sortFinalLHMDesc(counter.finalResult), args);


            LocalTime end = LocalTime.now();
            long sec = ChronoUnit.SECONDS.between(start, end);

            System.out.println("Zakończono! / Completed!\nCzas pobierania i przetwarzania plików wyniósł " + sec + " sekund. / The download and processing time for the files was " + sec +" seconds.");
        } else {
            System.out.println("Błąd! Nie znaleziono linków do albumów.");
            System.out.println("Kończenie pracy programu...");
            System.exit(0);
        }
    }

    static class Counter {
        LinkedHashMap<String, Integer> finalResult = new LinkedHashMap<>();

        public void add(LinkedHashMap<String, Integer> inputLhm) {
            synchronized (this) {
                for (String word : inputLhm.keySet()) {
                    if (finalResult.containsKey(word)) {
                        finalResult.put(word, finalResult.get(word) + inputLhm.get(word));
                    } else {
                        finalResult.put(word, inputLhm.get(word));
                    }
                }
            }
        }

        public LinkedHashMap<String, Integer> getFinalResult() {
            return finalResult;
        }
    }


    static List<Integer> downloaderWorkNumbersArray(int albumsPerDownloader, int downloaderNumber) {
        List<Integer> al = new ArrayList<>();
        for (int i = 1; i <= albumsPerDownloader; i++) {
            al.add(downloaderNumber * i - 1);
        }
        return al;
    }

    static void downloadPartOfAlbums(List<String> albumLinks, ArrayList<Integer> numbers, Counter counter, String[] args) {
        for (int a : numbers) {
            String album = albumLinks.get(a);
            System.out.println("POBIERANIE ALBUMU... / DOWNLOADING ALBUM... (" + album + ")");
            //albumLyricsDownloader(albumLinks);
        }
    }

    static String nameFromLink(String link) {
        return link.replace("https://genius.com/", "")
                .replace("-lyrics", "")
                .replaceAll("-", " ")
                .replaceFirst(" ", " - ")
                .replaceAll("ą", "a")
                .replaceAll("ć", "c")
                .replaceAll("ę", "e")
                .replaceAll("ł", "l")
                .replaceAll("ń", "n")
                .replaceAll("ó", "o")
                .replaceAll("ś", "s")
                .replaceAll("ż", "z")
                .replaceAll("ź", "z");
    }

    static String nameFromLinkBeta(String link) { //VERY BUGGY, NOT WORKING NOW
        try {
            Document document = Jsoup.connect(link).get();
            String result = document.select("div[class^=header_with_cover_art-primary_info]").toString();
            result = result
                    .replaceAll("\\s+", " ")
                    .replaceAll("<.*?>", " ")
                    .replaceAll("\\s", "-")
                    .replaceAll("-{2,100}", "\n")
                    .replaceFirst("\\s+", "")
                    .replaceAll("-", " ");

            result = result.replaceAll("&amp;", "&");
            System.out.println(result);
            Scanner scanner = new Scanner(result);
            String title = scanner.nextLine();
            String artist = scanner.nextLine();
            return artist + " - " + title;
        } catch (IOException e) {
            return "Błąd! Nie udało się pobrać tytułu i artysty. / Error! Cannot download title and artist.";
        }
    }

    static List<String> getAlbumLinks(String[] args) {
        List<String> arrayLinks = new ArrayList<>();
        try {
            String directory = args[0].replaceAll("\"", "");
            String links = new String(Files.readAllBytes(Paths.get(directory)));
            links = links.replaceAll("\\s+", "")
                    .replaceFirst("<", "\n")
                    .replaceAll("\"https://genius.com/albums/[A-Za-z0-9/-]*\"", "\n\n\n$0\n\n\n")
                    .replaceAll("\\s+[^\"].*[^\"]\\s+", "\n")
                    .replaceFirst("\\s+", "")
                    .replaceAll("\"", "")
                    .replaceAll("\\s+", ",");

            Object[] oa = Arrays.stream(links.split(",")).distinct().toArray();
            for (Object a : oa) {
                if (a.toString().contains("albums")) {
                    arrayLinks.add(a.toString());
                }
            }

            return arrayLinks;

        } catch (IOException e) {
            return null;
        }
    }

    static List<String> getTrackLinks(String album_URL) {
        List<String> arrayLinks = new ArrayList<>();
        try {
            Document albumLinks = Jsoup.connect(album_URL).get();
            String links = albumLinks.select("div[class^=\"column_layout-column_span column_layout-column_span--primary\"]").toString();
            links = links.replaceAll("</div>", "")
                    .replaceAll("<div.*?>", "")
                    .replaceAll("<a href=", "")
                    .replaceAll("<.*?>", "")
                    .replaceAll("\\s+", " ")
                    .replaceAll("class=.*? ", " ")
                    .replaceAll("\\s+", "");

            Object[] oa = Arrays.stream(links.split("\"")).toArray();
            for (Object a : oa) {
                if (a.toString().contains("-lyrics")) {
                    arrayLinks.add(a.toString());
                }
            }

            return arrayLinks;

        } catch (IOException e) {
            return null;
        }
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

    static List<String> albumLyricsDownloader(String albumLink) {

        System.out.println("WYSZUKIWANIE LINKÓW DO UTWORÓW NA STRONIE / SEARCHING FOR LINKS ON PAGE: " + albumLink);

        List<String> currentAlbum = getTrackLinks(albumLink);

        return currentAlbum;
    }

    static void lyricsDownloader(String link, Counter counter, String[] args) {
        System.out.println("Pobieranie tekstów utworów... / Downloading lyrics...");

        String tytul = nameFromLink(link);
        String result = downloadLyricsFromLink(link);
        result = (result != null) ? result : "[Brak tekstu]";
        saveLyricsTxt(result, tytul, args);
        saveStatsTxt(result, tytul, counter, args);

    }

    static void saveLyricsTxt(String output, String name, String[] args) {
        try {
            //LOKALIZACJA ZAPISU PLIKÓW Z TEKSTAMI
            File dir = new File(args[1].replaceAll("\"", ""));
            File file = new File(dir + "\\" + name + ".txt");
            if (!file.exists()) {
                boolean mkdir = dir.mkdirs();
            }
            FileWriter writer = new FileWriter(file);
            writer.write(output);
            writer.close();
        } catch (IOException e) {
            System.out.println("Error! Saving lyrics ended with error.");
        }
    }

    static void saveStatsTxt(String output, String name, Counter counter, String[] args) {
        LinkedHashMap<String, Integer> lhm = new LinkedHashMap<>();
        LinkedHashMap<String, Integer> lhm2 = new LinkedHashMap<>();
        String[] words = output.toLowerCase()
                .replaceAll("\\[.*\\]", "")
                .replaceAll("\\s+", " ")
                .replaceAll("[().,!?\"]", "")
                .split(" ");
        for (String w : words) {
            if (lhm.containsKey(w)) {
                lhm.put(w, lhm.get(w) + 1);
            } else {
                lhm.put(w, 1);
            }
        }
        lhm2 = sortFinalLHMDesc(lhm);

        try {
            //LOKALIZACJA ZAPISU PLIKÓW ZE STATYSTYKAMI
            File dir = new File(args[2].replaceAll("\"", ""));
            File file = new File(dir + "\\" + name + "_STATS.txt");
            if (!file.exists()) {
                boolean mkdir = dir.mkdirs();
            }
            FileWriter writer = new FileWriter(file);
            for (String k : lhm2.keySet()) {
                writer.write(k + ": " + lhm2.get(k) + "\n");
            }
            writer.close();

            updateFinalLHM(counter, lhm);

        } catch (IOException e) {
            System.out.println("Error! Saving stats ended with error.");
        }
    }

    static void megaStatsWriter(LinkedHashMap<String, Integer> lhm, String[] args) {
        try {
            File dir = new File(args[2].replaceAll("\"", ""));
            File file = new File(dir + "\\.MEGASTATS.txt");
            if (!file.exists()) {
                boolean mkdir = dir.mkdirs();
            }
            FileWriter writer = new FileWriter(file);
            for (String key : lhm.keySet()) {
                writer.write(key + ": " + lhm.get(key) + "\n");
            }
            writer.close();
        } catch (IOException e) {
            System.out.println("Error! Saving MEGAstats ended with error.");
        }

    }

    static void updateFinalLHM(Counter counter, LinkedHashMap<String, Integer> inputLhm) {
        counter.add(inputLhm);
    }

    static LinkedHashMap<String, Integer> sortFinalLHMDesc(LinkedHashMap<String, Integer> finalLhm) {
        LinkedHashMap<String, Integer> lhm = new LinkedHashMap<>();
        List<Map.Entry<String, Integer>> final_list = new ArrayList<Map.Entry<String, Integer>>(finalLhm.entrySet());

        Collections.sort(final_list, new Comparator<Map.Entry<String, Integer>>() {
            @Override
            public int compare(Map.Entry<String, Integer> o1, Map.Entry<String, Integer> o2) {
                return o2.getValue() - o1.getValue();
            }
        });

        for (Map.Entry<String, Integer> entry : final_list) {
            lhm.put(entry.getKey(), entry.getValue());
        }
        return lhm;
    }
}
