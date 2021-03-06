package com.metacodestudio.hotsuploader.files;

import com.metacodestudio.hotsuploader.models.ReplayFile;
import com.metacodestudio.hotsuploader.models.Status;
import com.metacodestudio.hotsuploader.utils.OSUtils;
import javafx.application.Platform;
import javafx.collections.ObservableList;

import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Queue;

import static java.nio.file.StandardWatchEventKinds.*;

public class WatchHandler implements Runnable {

    private final WatchService watchService;
    private Map<Status, ObservableList<ReplayFile>> fileMap;
    private Queue<ReplayFile> uploadQueue;

    private List<File> modifiedFiles;
    private Path path;

    public WatchHandler(final Path path, final Map<Status, ObservableList<ReplayFile>> fileMap, final Queue<ReplayFile> uploadQueue) throws IOException {
        this.path = path;
        watchService = FileSystems.getDefault().newWatchService();
        path.register(watchService, ENTRY_CREATE);
        this.modifiedFiles = new ArrayList<>();
        this.fileMap = fileMap;
        this.uploadQueue = uploadQueue;
    }

    @SuppressWarnings("unchecked")
    @Override
    public void run() {
        for (; ; ) {
            WatchKey key;
            try {
                key = watchService.take();
            } catch (InterruptedException e) {
                return;
            }
            System.out.println("Watch event!");

            for (final WatchEvent<?> watchEvent : key.pollEvents()) {
                WatchEvent.Kind<?> kind = watchEvent.kind();
                if (kind == OVERFLOW) {
                    continue;
                }
                WatchEvent<Path> event = (WatchEvent<Path>) watchEvent;
                final Path fileName = event.context();
                System.out.println("\t" + fileName);

                File file = new File(path.toFile(), fileName.toString());
                if (!file.getName().endsWith(".StormReplay")) {
                    System.out.println("\tInvalid file");
                    continue;
                }
                Handler handler;
                /*
                if(modifiedFiles.contains(file)) {
                    continue;
                } else {
                    modifiedFiles.add(file);
                }
                */
                if (kind == ENTRY_MODIFY) {
                    handler = new ModificationHandler(file);
                } else {
                    handler = new CreationHandler(file);
                }
                ReplayFile replayFile = handler.getFile();
                File propertiesFile = OSUtils.getPropertiesFile(file);
                if(propertiesFile.exists()) {
                    propertiesFile.delete();
                }
                System.out.println(replayFile.getStatus());
                Platform.runLater(() -> fileMap.get(Status.NEW).add(replayFile));
                uploadQueue.add(replayFile);

                boolean valid = key.reset();
                if (!valid) {
                    break;
                }
            }
        }
    }

    private abstract class Handler {

        private final File target;

        public Handler(File target) {
            this.target = target;
        }

        protected File getTarget() {
            return target;
        }

        public abstract ReplayFile getFile();

    }

    private class CreationHandler extends Handler {

        public CreationHandler(final File file) {
            super(file);
            System.out.println("CreationHandler");
        }

        @Override
        public ReplayFile getFile() {
            try {
                Thread.sleep(5000L);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            System.out.println("New file: " + getTarget().toString());
            File file = new File(getTarget().toString());
            return new ReplayFile(file);
        }
    }

    private class ModificationHandler extends Handler {

        public ModificationHandler(File target) {
            super(target);
            System.out.println("Modify handler");
        }

        @Override
        public ReplayFile getFile() {
            long stamp = 0;
            try {
                do {
                    System.out.println("Waiting");
                    System.out.println(getTarget().getName());

                    Thread.sleep(10000L);
                    System.out.println(stamp);
                    System.out.println(System.currentTimeMillis() - 20000L);
                } while ((stamp = getTarget().lastModified()) > System.currentTimeMillis() - 20000L);
                return new ReplayFile(new File(getTarget().toString()));
            } catch (InterruptedException e) {
                return null;
            }
        }
    }

}
