package nio;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Iterator;
import java.util.Set;
import java.util.stream.Collectors;

public class NioServer {

    private final ServerSocketChannel serverChannel = ServerSocketChannel.open();
    private final Selector selector = Selector.open();
    private final ByteBuffer buffer = ByteBuffer.allocate(5);
    private final String START_DIR="serverDir";
    private Path serverPath = Paths.get("serverDir");

    public NioServer() throws IOException {
        serverChannel.bind(new InetSocketAddress(8189));
        serverChannel.configureBlocking(false);
        serverChannel.register(selector, SelectionKey.OP_ACCEPT);
        while (serverChannel.isOpen()) {
            selector.select(); // block
            System.out.println("Что-то пришло");
            Set<SelectionKey> keys = selector.selectedKeys();
            Iterator<SelectionKey> iterator = keys.iterator();
            while (iterator.hasNext()) {
                SelectionKey key = iterator.next();
                iterator.remove();
                if (key.isAcceptable()) {
                    handleAccept(key);
                }
                if (key.isReadable()) {
                    handleRead(key);
                }
            }
        }
    }

    public static void main(String[] args) throws IOException {
        new NioServer();
    }

    private void handleRead(SelectionKey key) throws IOException {
        SocketChannel channel = (SocketChannel) key.channel();
        int read = 0;
        StringBuilder msg = new StringBuilder();
        while ((read = channel.read(buffer)) > 0) {
            buffer.flip();
            while (buffer.hasRemaining()) {
                msg.append((char) buffer.get());
            }
            buffer.clear();
        }
        String command = msg.toString().replaceAll("[\n|\r]", "");
        // Команда ls
        if (command.equals("ls")) {
            String files = Files.list(serverPath)
                    .map(path -> path.getFileName().toString())
                    .collect(Collectors.joining(", "));
            files += "\r\n";
            channel.write(ByteBuffer.wrap(files.getBytes(StandardCharsets.UTF_8)));
        }
        // Команда cat, если введена эта команда с пробелом то ищет файл в находящейся дирректории
        if (command.startsWith("cat ")) {
            String fileName=checkCommand(command, channel);
            if(fileName!=null)
          {
                Path serverFilePath = Paths.get(serverPath.toString(), "/" + fileName);
                if (Files.notExists(serverFilePath)) {
                    channel.write(ByteBuffer.wrap("File not found\n\r".getBytes(StandardCharsets.UTF_8)));
                } else {
                    channel.write(ByteBuffer.wrap(Files.readAllBytes(serverFilePath)));
                    channel.write(ByteBuffer.wrap("\n\r".getBytes(StandardCharsets.UTF_8)));
                }
            }
        }
       //создает новый файл
        if(command.startsWith("touch ")){
            String fileName=checkCommand(command, channel);
            if(fileName!=null)
            {
                Path serverFilePath = Paths.get(serverPath.toString(), "/" + fileName);
                if (Files.notExists(serverFilePath)) {
                    Files.createFile(serverFilePath);
                    channel.write(ByteBuffer.wrap("File created\n\r".getBytes(StandardCharsets.UTF_8)));
                } else {
                    channel.write(ByteBuffer.wrap("File already exist\n\r".getBytes(StandardCharsets.UTF_8)));
                }
            }
        }

        //создает новую папку
        if(command.startsWith("mkdir ")){
            String folderName=checkCommand(command, channel);
            if(folderName!=null)
            {
                Path serverFolderPath = Paths.get(serverPath.toString(), "/" + folderName);
                if (Files.notExists(serverFolderPath)) {
                    Files.createDirectory(serverFolderPath);
                    channel.write(ByteBuffer.wrap("Folder created\n\r".getBytes(StandardCharsets.UTF_8)));
                } else {
                    channel.write(ByteBuffer.wrap("Folder already exist\n\r".getBytes(StandardCharsets.UTF_8)));
                }
            }
        }

     //переход к выбранному каталогу
        if (command.startsWith("cd ")) {
            String folderName=checkCommand(command, channel);
            if(folderName!=null)
            {
                    Path serverFolderPath = Paths.get(serverPath.toString(), "/" + folderName);
                    if (Files.notExists(serverFolderPath)) {
                        channel.write(ByteBuffer.wrap("Folder not exist\n\r".getBytes(StandardCharsets.UTF_8)));
                    } else {
                        serverPath=serverFolderPath;
                    }
                }
            }

        //возврат на папку выше
        if (command.equals("cd..")) {
            if(!serverPath.toString().equals(START_DIR)){
                serverPath=serverPath.getParent();
            }else {channel.write(ByteBuffer.wrap("This is your start catalog\n\r".getBytes(StandardCharsets.UTF_8)));}
        }




        //команда для отображения настоящей дирректории
        channel.write(ByteBuffer.wrap((serverPath.toString() + ">").getBytes(StandardCharsets.UTF_8)));

    }
    //Функция проверки команды на 2 слова
    private String checkCommand(String check, SocketChannel channel) throws IOException {
        String[] words = check.split(" ");
        if (words.length != 2) {
            channel.write(ByteBuffer.wrap("Wrong command\n\r".getBytes(StandardCharsets.UTF_8)));
            return null;

        } else {
            String fileName = words[1];
            return fileName;
        }
    }

    private void handleAccept(SelectionKey key) throws IOException {
        SocketChannel channel = ((ServerSocketChannel) key.channel()).accept();
        channel.configureBlocking(false);
        channel.register(selector, SelectionKey.OP_READ);
        System.out.println(channel.toString());
    }
}