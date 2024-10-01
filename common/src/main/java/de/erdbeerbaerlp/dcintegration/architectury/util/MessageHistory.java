package de.erdbeerbaerlp.dcintegration.architectury.util;

import de.erdbeerbaerlp.dcintegration.common.DiscordIntegration;
import de.erdbeerbaerlp.dcintegration.common.storage.Configuration;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.PlayerChatMessage;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.MinecraftServer;
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
            if (initialized) return;
            FileIO.init();
            messages = FileIO.readAndCleanUpMessages();
            // done, record
            initialized = true;
            String x = "DiscordIntegration MessageHistory initialized: " +
                String.format("loaded %,d messages...", messages.size());
            addMessage(new SimpleChatMessage(x));
            DiscordIntegration.LOGGER.info(x);
        }
        catch (Exception e) {
            messages = emptyList();
            DiscordIntegration.LOGGER.error(
                "DiscordIntegration MessageHistory not successfully initialized: " +
                String.format("loaded %,d messages...", messages.size()
            ), e);
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
        if (!enableDuplicatesCheck)
            // to disable duplicates check, then early return dummy result
            // where hasDuplicate == false
            return new DuplicateCheckResult(false, messages.size(), 0, msg);
        if (msg.message().contains("starting")) init();
        if (msg.message().contains("stopping")) shutdown();
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
            String errorMsg = String.format("Exception in countDuplicates(SimpleChatMessage %s)", msg.toString());
            DiscordIntegration.LOGGER.warn(errorMsg, e);
            return 0;
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
    // note: file should be synced with messages List or behavior will not be correct
    private void addMessage(SimpleChatMessage msg) {
        if (msg == null) throw new NullPointerException();
        messages.add(msg);
        FileIO.appendMessage(msg);
    }
    // #endregion Add

    // #region Remove
    // note: file should be synced with messages List or behavior will not be correct
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
    private static String toString(List<SimpleChatMessage> msgs) {
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

    private static JsonObject backOf(List<JsonObject> list) {
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

    public DuplicateCheckResult checkDuplicate(MinecraftServer server, String message) {
        return checkDuplicate(new SimpleChatMessage(server, message));
    }

    public DuplicateCheckResult checkDuplicate(CommandSourceStack source, String message) {
        return checkDuplicate(new SimpleChatMessage(source, message));
    }

    public String toString() {
        // useful for debugging
        return toString(messages);
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
        // non-default
        public SimpleChatMessage(ServerPlayer sender, PlayerChatMessage signedMessage) {
            // Values should be assigned no matter what using null checks
            setPlayerID(sender);
            setUsername(sender);
            setMessage(signedMessage);
            setDate();
        }

        public SimpleChatMessage(ServerPlayer sender, String text) {
            // for non-chat-based events
            setPlayerID(sender);
            setUsername(sender);
            setMessage(text);
            setDate();
        }

        public SimpleChatMessage(MinecraftServer server, String text) {
            // for server messages
            setPlayerID(server);
            setUsername(server);
            setMessage(text);
            setDate();
        }

        public SimpleChatMessage(CommandSourceStack source, String text) {
            // for commands
            setPlayerID(source);
            setUsername(source);
            setMessage(text);
            setDate();
        }

        public SimpleChatMessage(String text) {
            // for internal use
            setMessage(text);
            setDate();
        }
        // #endregion Construct

        // #region Accessors
        public String playerID() {
            try {
                if (playerID == null) throw new NullPointerException();
                return playerID;
            }
            catch (Exception e) {
                return "0000000";
            }
        }

        private String toPlayerID(ServerPlayer sender) {
            try {
                if (sender == null) throw new NullPointerException();
                return sender.getUUID().toString();
            }
            catch (Exception e) {
                return null;
            }
        }

        // different syntax for server
        private String toPlayerID(MinecraftServer server) {
            try {
                if (server == null) throw new NullPointerException();
                return "Server"; // ! is there a more fitting way to get id from server obj?
            }
            catch (Exception e) {
                return null;
            }
        }

        // different syntax for commands
        private String toPlayerID(CommandSourceStack source) {
            try {
                if (source == null) throw new NullPointerException();
                return source.getEntity().getUUID().toString();
            }
            catch (Exception e) {
                return null;
            }
        }

        // #region Setters
        private void setPlayerID(ServerPlayer sender) {
            try {
                if (sender == null) throw new NullPointerException();
                playerID = toPlayerID(sender);
            }
            catch (NullPointerException e) {
                playerID = null;
            }
        }

        private void setPlayerID(MinecraftServer server) {
            try {
                if (server == null) throw new NullPointerException();
                playerID = toPlayerID(server);
            }
            catch (NullPointerException e) {
                playerID = null;
            }
        }

        private void setPlayerID(CommandSourceStack source) {
            try {
                if (source == null) throw new NullPointerException();
                playerID = toPlayerID(source);
            }
            catch (NullPointerException e) {
                playerID = null;
            }
        }

        private void setPlayerID(String server) {
            try {
                if (source == null) throw new NullPointerException();
                playerID = server;
            }
            catch (NullPointerException e) {
                playerID = null;
            }
        }
        // #endregion Setters

        // username
        public String username() {
            try {
                if (username == null) throw new NullPointerException();
                return username;
            }
            catch (NullPointerException e) {
                return "Unknown Username";
            }
        }

        private String toUsername(ServerPlayer sender) {
            try {
                if (sender == null) throw new NullPointerException();
                return FileIO.fetchUsername(toPlayerID(sender));
            }
            catch (Exception e) {
                return null;
            }
        }

        // for server
        private String toUsername(MinecraftServer server) {
            try {
                if (server == null) throw new NullPointerException();
                return "Server"; // ! is there another more fitting way to get name from server obj? if not, honestly this is already pretty solid
            }
            catch (Exception e) {
                return null;
            }
        }

        // for commands
        private String toUsername(CommandSourceStack source) {
            try {
                if (source == null) throw new NullPointerException();
                return source.getTextName();
            }
            catch (Exception e) {
                return null;
            }
        }

        // #region Setters
        private void setUsername(ServerPlayer sender) {
            try {
                username = toUsername(sender);
            }
            catch (NullPointerException e) {
                username = null;
            }
        }

        private void setUsername(MinecraftServer server) {
            try {
                username = toUsername(server);
            }
            catch (NullPointerException e) {
                username = null;
            }
        }

        private void setUsername(CommandSourceStack source) {
            try {
                username = toUsername(source);
            }
            catch (NullPointerException e) {
                username = null;
            }
        }

        private void setUsername(String sender) {
            try {
                username = sender;
            }
            catch (NullPointerException e) {
                username = null;
            }
        }
        // #endregion Setters

        // message
        public String message() {
            try {
                if (message == null) throw new NullPointerException();
                return message;
            }
            catch (Exception e) {
                return "Unknown Message";
            }
        }

        private String toMessage(PlayerChatMessage signedMessage) {
            try {
                if (signedMessage == null) throw new NullPointerException();
                return signedMessage.signedContent();
            }
            catch (Exception e) {
                return null;
            }
        }

        // #region Setters
        private void setMessage(PlayerChatMessage signedMessage) {
            try {
                message = toMessage(signedMessage);
            }
            catch (NullPointerException e) {
                message = null;
            }
        }

        private void setMessage(String text) {
            try {
                message = text;
            }
            catch (NullPointerException e) {
                message = null;
            }
        }
        // #endregion Setters

        public String date() {
            try {
                return date.toString();
            }
            catch (Exception e) {
                return "Unknown date";
            }
        }

        // #region Setters
        private void setDate() {
            try {
                date = LocalDate.now();
            }
            catch (Exception e) { 
                return;
            }
        }
        // #endregion Setters

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
        // #region Construct
        private static final String JSON_FILE_PATH = "DiscordIntegration-Data/History.json";
        private static final Gson gson = new GsonBuilder()
            .setPrettyPrinting()
            .registerTypeAdapter(LocalDate.class, new LocalDateAdapter())
            .create();

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

        public static void init() {
            ensureHistoryFileExists();
        }

        private static void ensureHistoryFileExists() {
            try {
            File file = new File(JSON_FILE_PATH);
            mkdir(file.getParentFile());
            createFile(file);
            }
            catch (IOException e) {
                DiscordIntegration.LOGGER.error("Error in ensureHistoryFileExists():", e);
            }
        }
        // #endregion Construct

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

        public static String fetchUsername(String uuid) {
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

        private static void mkdir(File parentDir) throws IOException {
            boolean success = parentDir.exists() || parentDir.mkdirs();
            if (!success)
                throw new IOException("Could not create directory: " + parentDir);
        }

        private static void createFile(File file) throws IOException {
            boolean success = file.exists() || file.createNewFile();
            if (!(success))
                throw new IOException("Could not create file: " + file);
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
