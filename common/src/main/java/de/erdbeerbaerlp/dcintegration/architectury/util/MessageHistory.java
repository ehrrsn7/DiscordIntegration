package de.erdbeerbaerlp.dcintegration.architectury.util;

import de.erdbeerbaerlp.dcintegration.common.DiscordIntegration;
import de.erdbeerbaerlp.dcintegration.common.storage.Configuration;
import net.minecraft.network.chat.PlayerChatMessage;
import net.minecraft.server.level.ServerPlayer;
import com.google.gson.*;
import com.google.gson.stream.*;
import com.google.gson.reflect.TypeToken;
import java.io.*;
import java.util.*;
import java.util.stream.*;
import java.lang.reflect.Type;
import java.net.UnknownHostException;
import java.net.URL;
import java.time.LocalDate;

// handle duplicate messages
public class MessageHistory { // ! Convert to Singleton? Note static probably isn't appropriate
    // #region Construct
    private boolean initialized = false;
    private boolean enableDuplicatesCheck = loadFromConfig("Config.Messages.ignoreDuplicateMessages");
    // Keep record of messages in memory
    private List<SimpleChatMessage> messages = new ArrayList<>();

    public MessageHistory() {
        if (!initialized) init();
    }

    public void init() {
        try {
            FileIO.init();
            messages = FileIO.readAndCleanUpMessages();
            // done, record
            initialized = true;
            DiscordIntegration.LOGGER.info(String.format(
                "MessageHistory initialized: loaded %,d messages...",
                messages.size()
            ));
        }
        catch (Exception e) {
            messages = emptyList();
            DiscordIntegration.LOGGER.warn(String.format(
                "MessageHistory not successfully initialized: loaded %,d messages...",
                messages.size()
            ));
        }
    }

    private boolean loadFromConfig(String which) {
        try {
            if (which.equals("Config.Messages.ignoreDuplicateMessages"))
                return loadDuplicatesConfig();
            throw new Exception(String.format("Unknown configuration specified: %s", which));
        }
        catch (Exception e) {
            DiscordIntegration.LOGGER.warn("Error loading configuration: ", which);
            return false;
        }
    }

    private boolean loadDuplicatesConfig() {
        // ! Fix
        System.out.println(String.format(
            "Loading configuration: Configuration.instance().messages: %s\n" +
            "\t etc: ",
            Configuration.instance().messages
        ));
        // return Configuration.instance().messages.ignoreDuplicateMessages;
        return true; // debug value
    }

    // Destruct
    public void shutdown() {
        FileIO.cleanUpAndWriteMessages(messages);
        DiscordIntegration.LOGGER.info(String.format(
            "MessageHistory stopping: saving %,d messages...",
            messages.size()
        ));
    }
    // #endregion Construct

    // public methods

    public DuplicateCheckResult checkDuplicate(SimpleChatMessage msg) {
        if (msg.message().contains("Server stopped")) shutdown();
        long amountFound = countDuplicates(msg);
        boolean hasDuplicate = amountFound > 0;
        if (!hasDuplicate) addMessage(msg);
        else DiscordIntegration.LOGGER.warn("Duplicate found!");
        return new DuplicateCheckResult(
            hasDuplicate,
            messages.size(),
            amountFound,
            msg
        );
    }

    public record DuplicateCheckResult(
        boolean hasDuplicate,
        long amountTotal,
        long amountFound,
        SimpleChatMessage message
    ) { }

    // private methods

    // #region Handle Duplicates
    private boolean hasDuplicate(SimpleChatMessage msg) {
        // to fix bugs, just call this and compare with 0
        return countDuplicates(msg) > 0;
    }

    private long countDuplicates(SimpleChatMessage msg) {
        try {
            // if any errors, just return 0
            if (msg == null || messages == null)
                throw new NullPointerException();
            if (messages.size() <= 0) return 0;

            return filterDuplicates(msg).count();
        }
        catch (Exception e) {
            DiscordIntegration.LOGGER.warn(
                String.format("Exception in countDuplicates(SimpleChatMessage %s)", msg.toString()), e);
            return -1;
        }
    }

    private Stream<SimpleChatMessage> filterDuplicates(SimpleChatMessage msg) {
        // all errors caught in public callers, if any
        return messages.stream()
            // date needs to match but time doesn't matter, so just filter
            .filter(SimpleChatMessage::matchesToday)
            // only returns exact matches of playerID AND message
            .filter(element -> element.matchesPlayerID(msg))
            .filter(element -> element.matchesMessage(msg));
    }
    // #endregion Handle Duplicates

    // #region Add
    private void addMessage(SimpleChatMessage msg) {
        if (msg == null) throw new NullPointerException();
        messages.add(msg);
        FileIO.appendMessage(msg);
    }
    // #endregion Add

    // #region Remove
    private void removeOldMessages() {
        messages.removeIf(element -> !element.matchesToday());
        FileIO.writeMessages(messages); // sync with file
    }

    private void remove(SimpleChatMessage msg) {
        messages.removeIf(element -> !element.matchesToday());
        FileIO.writeMessages(messages); // sync with file
    }

    private static List<SimpleChatMessage> removeOldMessages(List<SimpleChatMessage> msgs) {
        msgs.removeIf(element -> !element.matchesToday());
        return msgs;
    }
    // #endregion Remove

    // #region Accessors
    private static String allMessagesToString(List<SimpleChatMessage> msgs) {
        List<String> l = msgs.stream()
            .map(SimpleChatMessage::toString)
            .collect(Collectors.toList());
        return String.join(",\n", l);
    }

    private static List<SimpleChatMessage> emptyList() {
        return new ArrayList<>();
    }

    private static int lastIndex(List<JsonObject> list) {
        return list.size() - 1;
    }

    private static JsonObject topOf(List<JsonObject> list) {
        return list.get(lastIndex(list));
    }
    // #endregion Accessors

    // #region Useful Overloaded Methods
    public DuplicateCheckResult checkDuplicate(ServerPlayer sender, PlayerChatMessage signedMessage) {
        return checkDuplicate(new SimpleChatMessage(sender, signedMessage));
    }

    public DuplicateCheckResult checkDuplicate(ServerPlayer sender, String message) {
        return checkDuplicate(new SimpleChatMessage(sender, message));
    }

    private boolean hasDuplicate(ServerPlayer sender, PlayerChatMessage signedMessage) {
        return hasDuplicate(new SimpleChatMessage(sender, signedMessage));
    }

    private boolean hasDuplicate(ServerPlayer sender, String message) {
        return hasDuplicate(new SimpleChatMessage(sender, message));
    }

    public String allMessagesToString() {
        // useful for debugging
        return allMessagesToString(messages);
    }
    // #endregion Useful Overloaded Methods

    // nested class for message instances in List
    public class SimpleChatMessage {
        // #region Construct
        // Properties
        private String playerID = new String();
        private String username = new String();
        private String message = new String();
        private LocalDate date = LocalDate.now();

        // Constructors
        // default
        public SimpleChatMessage() {
            setPlayerID(null);
            setUsername(null);
            setMessage(null);
            setDate();
        }

        // non-default
        public SimpleChatMessage(ServerPlayer sender, PlayerChatMessage signedMessage) {
            // Values should be assigned no matter what using null checks
            setPlayerID(sender);
            setUsername(sender);
            setMessage(signedMessage);
            setDate();
        }

        public SimpleChatMessage(ServerPlayer sender, String text) {
            // Values should be assigned no matter what using null checks
            setPlayerID(sender);
            setUsername(sender);
            setMessageText(text);
            setDate();
        }
        // #endregion Construct

        // #region Accessors
        // accessors
        public String playerID() {
            try {
                if (playerID == null) throw new NullPointerException();
                return playerID;
            }
            catch (Exception e) {
                return "Unknown Sender";
            }
        }

        public String toPlayerID(ServerPlayer sender) {
            try {
                return sender.getUUID().toString();
            }
            catch (Exception e) {
                return null;
            }
        }

        private void setPlayerID(ServerPlayer sender) {
            try {
                playerID = toPlayerID(sender);
            }
            catch (NullPointerException e) {
                playerID = null;
            }
        }

        public String username() {
            try {
                if (username == null) throw new NullPointerException();
                return username;
            }
            catch (Exception e) {
                return "Unknown Sender";
            }
        }

        private void setUsername(ServerPlayer sender) {
            try {
                username = FileIO.fetchUsername(toPlayerID(sender));
            }
            catch (NullPointerException e) {
                username = null;
            }
        }

        public String message() {
            try {
                if (message == null) throw new NullPointerException();
                return message;
            }
            catch (Exception e) {
                return "Unknown Message";
            }
        }

        private void setMessage(PlayerChatMessage signedMessage) {
            try {
                message = signedMessage.signedContent();
            }
            catch (NullPointerException e) {
                message = null;
            }
        }

        private void setMessageText(String text) {
            try {
                message = text;
            }
            catch (NullPointerException e) {
                message = null;
            }
        }

        private void setDate() {
            try {
                date = LocalDate.now();
            }
            catch (Exception e) { } // shouldn't really get to this point
        }

        public String toString() {
            // error handling done in individual get methods
            return "{\n" +
                String.format("\tplayerID: %s,\n", playerID()) +
                String.format("\tusername: %s,\n", username()) +
                String.format("\tmessage: %s,\n", message()) +
                String.format("\tdate: %s,\n", date.toString()) +
            "}";
        }
        // #endregion Accessors

        // #region Match Helpers
        private boolean matchesMessage(SimpleChatMessage msg) {
            try {
                if (msg == null || message == null) throw new NullPointerException();
                return msg.message().equals(message);
            }
            catch (NullPointerException e) {
                return false;
            }
        }

        private boolean matchesPlayerID(SimpleChatMessage msg) {
            try {
                if (playerID == null || msg == null) throw new NullPointerException();
                return msg.playerID().equals(playerID);
            }
            catch (Exception e) {
                return false;
            }
        }

        private boolean matchesToday() {
            try {
                if (date == null) throw new NullPointerException();
                return LocalDate.now().isEqual(date); // ours is today
            }
            catch (NullPointerException e) {
                DiscordIntegration.LOGGER.error("Error: date should never be null:", e);
                return false;
            }
        }
        // #endregion Match Helpers
    }

    private class FileIO {
        // #region Properties
        private static final Gson gson = new GsonBuilder()
            .setPrettyPrinting()
            .registerTypeAdapter(LocalDate.class, new LocalDateAdapter())
            .create();
        private static final String JSON_FILE_PATH = "DiscordIntegration-Data/History.json";

        // Custom TypeAdapter for LocalDate
        private static class LocalDateAdapter extends TypeAdapter<LocalDate> {
            @Override
            public void write(JsonWriter out, LocalDate value) throws IOException {
                if (value == null) out.nullValue();
                else out.value(value.toString());
            }

            @Override
            public LocalDate read(JsonReader in) throws IOException {
                if (in.peek() == JsonToken.NULL) {
                    in.nextNull();
                    return null;
                }
                return LocalDate.parse(in.nextString());
            }
        }
        // #endregion Properties

        // #region Init
        public static void init() {
            ensureHistoryFileExists();
        }

        private static void ensureHistoryFileExists() {
            File file = new File(JSON_FILE_PATH);
            tryMkdir(file.getParentFile());
            tryCreateFile(file);
        }
        // #endregion Init

        // #region Read
        public static List<SimpleChatMessage> readMessages() {
            try (BufferedReader reader = new BufferedReader(new FileReader(JSON_FILE_PATH))) {
                checkEmptyFile(reader); // throws IOException if empty
                List<SimpleChatMessage> msgs = fromJson(reader);
                if (msgs == null) throw new FileNotFoundException();
                return msgs;
            }
            catch (IOException e) {
                DiscordIntegration.LOGGER.error("Error reading messages.");
                // If the file doesn't exist or file is empty, create a new empty list
                return emptyList();
            }
        }

        public static List<SimpleChatMessage> readAndCleanUpMessages() {
            // read messages from file to memory
            List<SimpleChatMessage> msgs = FileIO.readMessages();

            long previousSize = msgs.size();
            // clean up the list of messages on memory
            msgs = removeOldMessages(msgs);

            if (msgs.size() != previousSize)
                // update messages on file to match cleaned list on memory
                FileIO.writeMessages(msgs);

            // return a copy of the fresh list
            return msgs;
        }

        private static String fetchUsername(String uuid) {
            try (InputStreamReader reader = new InputStreamReader(
                new URL(String.format(
                    "https://playerdb.co/api/player/minecraft/%s",
                    uuid.replace("-", "")
                )).openStream()
            )) {
                JsonObject response = gson.fromJson(reader, JsonObject.class);
                JsonObject data = getAsJsonObject(response, "data");
                JsonObject player = getAsJsonObject(data, "player");
                return getAsString(player, "username");
            }
            catch (UnknownHostException e) {
                DiscordIntegration.LOGGER.warn("No internet, unable to fetch usernames.");
                return null;
            }
            catch (JsonSyntaxException e) {
                DiscordIntegration.LOGGER.warn("Failed to parse usernames JSON response.");
                return null;
            }
            catch (Exception e) {
                DiscordIntegration.LOGGER.warn("Failed to fetch usernames:", e);
                return null;
            }
        }

        private static String toUsername(JsonObject response) throws JsonSyntaxException {
            JsonObject data = getAsJsonObject(response, "data");
            JsonObject player = getAsJsonObject(data, "player");
            return getAsString(player, "username");
        }

        private static JsonObject getAsJsonObject(JsonObject response, String name) throws JsonSyntaxException {
            if (!response.has(name)) throw new JsonSyntaxException("Missing property " + name);
            return response.getAsJsonObject(name);
        }

        private static String getAsString(JsonObject response, String name) throws JsonSyntaxException {
            if (!response.has(name)) throw new JsonSyntaxException("Missing property " + name);
            return response.get(name).getAsString();
        }

        private static List<SimpleChatMessage> fromJson(BufferedReader reader) throws JsonSyntaxException {
            Type type = new TypeToken<ArrayList<SimpleChatMessage>>() {}.getType();
            return gson.fromJson(reader, type);
        }

        private static void checkEmptyFile(BufferedReader reader) throws IOException {
            reader.mark(1); // Mark the beginning of the file
            if (reader.read() == -1) // -1 indicates end of file
                throw new IOException("File is empty, creating empty array...");
            reader.reset(); // Reset the reader to the beginning of the file
        }
        // #endregion Read

        // #region Write
        public static void writeMessages(List<SimpleChatMessage> msgs) {
            try (FileWriter writer = new FileWriter(JSON_FILE_PATH)) {
                if (msgs == null) throw new NullPointerException();
                toJson(writer, msgs);
                writer.close();
            }
            catch (Exception e) {
                DiscordIntegration.LOGGER.error("Error: ", e);
            }
        }

        public static void cleanUpAndWriteMessages(List<SimpleChatMessage> msgs) {
            long previousSize = msgs.size();
            // clean up the list of messages on memory
            msgs = removeOldMessages(msgs);

            if (msgs.size() != previousSize)
                // update messages on file to match cleaned list on memory
                FileIO.writeMessages(msgs);
        }

        public static void appendMessage(SimpleChatMessage message) {
            try {
                List<SimpleChatMessage> msgs = readMessages();
                long previousCount = msgs.stream().count();
                long previousSize = msgs.size();
                msgs.add(message);
                if (msgs.stream().count() != previousCount + 1 ||
                    msgs.size() != previousSize + 1)
                    throw new Exception("Message not added properly");
                writeMessages(msgs);
            }
            catch (Exception e) {
                DiscordIntegration.LOGGER.error("Error: ", e);
            }
        }

        private static void tryMkdir(File parentDir) {
            try {
                boolean success = parentDir.exists() || parentDir.mkdirs();
                if (!success)
                    throw new IOException("Could not create directory: " + parentDir);
            }
            catch (IOException e) {
                DiscordIntegration.LOGGER.error("Error: ", e);
            }
        }

        private static void tryCreateFile(File file) {
            try {
                boolean success = file.exists() || file.createNewFile();
                if (!(success))
                    throw new IOException("Could not create file: " + file);
            }
            catch (IOException e) {
                DiscordIntegration.LOGGER.error("Error: ", e);
            }
        }

        private static void toJson(FileWriter writer, List<SimpleChatMessage> msgs) {
            gson.toJson(msgs, writer);
        }
        // #endregion Write
    }

    public static class Test {
        public static void testCheckEmptyFile() {
            // setup

            // execute
            // assert
            // teardown
        }
    }
}
