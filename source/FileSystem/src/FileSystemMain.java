import com.swirlds.platform.*;

import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class FileSystemMain implements SwirldMain {

    private final int sleepPeriod = 1000;
    public ArrayList<FileSystemPage> pages = new ArrayList<>();
    private Platform platform;
    private int selfId;
    private Console console;
    private String threadName;
    private ArrayList<Path> published;

    public static void main(String[] args) {
        Browser.main(null);
    }

    @Override
    public void preEvent() {
    }

    @Override
    public void init(Platform platform, int id) {
        this.platform = platform;
        this.selfId = id;
        this.console = platform.createConsole(true); // create the window, make it visible
        platform.setAbout("File System \n"); // set the browser's "about" box
        platform.setSleepAfterSync(sleepPeriod);
        this.published = new ArrayList<>();
    }

    private ArrayList<Path> directoryScan() throws IOException {

        ArrayList<Path> scannedFiles = new ArrayList<>();
        Files.walk(Paths.get("files/" + this.threadName)).filter(Files::isRegularFile).forEach(scannedFiles::add);
        return scannedFiles;
    }

    private void updateFileInNetwork(Path path) throws IOException {
        byte[] transaction = FileSystemEvent.Serialize(new FileSystemEvent(
                FileSystemEventType.MODIFY,
                path.toString(),
                1,
                Files.readAllBytes(path),
                this.threadName));
        platform.createTransaction(transaction, null);
    }

    // Event handler for delete file
    private void removeFileFromNetwork(Path path) throws IOException {
        byte[] transaction = FileSystemEvent.Serialize(new FileSystemEvent(
                FileSystemEventType.DELETE,
                path.toString(),
                1,
                null,
                this.threadName));
        platform.createTransaction(transaction, null);
    }


    // Event handler for new file
    private void addFileToNetwork(Path path) throws IOException {
        byte[] transaction = FileSystemEvent.Serialize(new FileSystemEvent(
                FileSystemEventType.CREATE,
                path.toString(),
                1,
                Files.readAllBytes(path),
                this.threadName));
        platform.createTransaction(transaction, null);
    }

    @Override
    public void run() {
        this.threadName = platform.getState().getAddressBookCopy().getAddress(selfId).getSelfName();

        byte[] transaction;

        ArrayList<Path> unpublished = new ArrayList<>();
        Path localDirectory = Paths.get("files/" + this.threadName);

        // Create a file watcher for the file system.
        WatchService watcher = null;
        WatchKey watchKey = null;
        try {
            watcher = localDirectory.getFileSystem().newWatchService();
            localDirectory.register(watcher, StandardWatchEventKinds.ENTRY_CREATE,
                    StandardWatchEventKinds.ENTRY_DELETE, StandardWatchEventKinds.ENTRY_MODIFY);
        } catch (IOException e) {
            e.printStackTrace();
        }

        String lastReceived = "";
        boolean isRunning = true;

        while (isRunning) {

            // Read network for files.
            FileSystemState state = (FileSystemState) platform.getState();
            String received = state.getReceived();

            if (!lastReceived.equals(received)) {
                lastReceived = received;
                for (FileSystemEvent event : state.getEvents()) {
                    console.out.println(received);
                }

                for (FileSystemPage page : state.getFileSystem()) {
                    File file = new File(page.getFileName());
                    try {
                        file.createNewFile();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }

            try {
                watchKey = watcher.poll(1000, TimeUnit.MILLISECONDS);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }

            if (watchKey != null) {
                List<WatchEvent<?>> events = watchKey.pollEvents();
                for (WatchEvent event : events) {
                    if (event.kind() == StandardWatchEventKinds.ENTRY_CREATE) {
                        console.out.println("Created: " + event.context().toString());
                        Path path = Paths.get("files/" + this.threadName + "/" + event.context().toString());
                        if (!Files.isDirectory(path)) {
                            try {
                                addFileToNetwork(path);
                            } catch (IOException e) {
                                e.printStackTrace();
                            }
                        }
                    }
                    if (event.kind() == StandardWatchEventKinds.ENTRY_DELETE) {
                        console.out.println("Delete: " + event.context().toString());
                        Path path = Paths.get("files/" + this.threadName + "/" + event.context().toString());
                        if (!Files.isDirectory(path)) {
                            try {
                                removeFileFromNetwork(path);
                            } catch (IOException e) {
                                e.printStackTrace();
                            }
                        }
                    }
                    if (event.kind() == StandardWatchEventKinds.ENTRY_MODIFY) {
                        console.out.println("Modify: " + event.context().toString());
                        Path path = Paths.get("files/" + this.threadName + "/" + event.context().toString());
                        if (!Files.isDirectory(path)) {
                            try {
                                updateFileInNetwork(path);
                            } catch (IOException e) {
                                e.printStackTrace();
                            }
                        }
                    }
                }
                watchKey.reset();
            }

            try {
                Thread.sleep(sleepPeriod);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    @Override
    public SwirldState newState() {
        return new FileSystemState();
    }
}