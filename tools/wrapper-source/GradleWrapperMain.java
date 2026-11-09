package org.gradle.wrapper;

import java.io.*;
import java.net.*;
import java.nio.file.*;
import java.util.*;
import java.util.zip.*;

/** Minimal offline-generated wrapper bootstrap for a standalone Android Studio project. */
public final class GradleWrapperMain {
    private GradleWrapperMain() {}

    public static void main(String[] args) throws Exception {
        File base = new File(System.getProperty("user.dir"));
        File propsFile = new File(base, "gradle/wrapper/gradle-wrapper.properties");
        if(!propsFile.exists()) throw new FileNotFoundException("Missing " + propsFile);
        Properties props = new Properties();
        try(InputStream in=new FileInputStream(propsFile)){props.load(in);}
        String distribution=props.getProperty("distributionUrl");
        if(distribution==null || !distribution.startsWith("https://"))
            throw new IllegalArgumentException("Only secure Gradle distributions are supported");
        String fileName=distribution.substring(distribution.lastIndexOf('/')+1);
        if(!fileName.matches("gradle-[0-9]+(?:\\.[0-9]+)*-(?:bin|all)\\.zip"))
            throw new IllegalArgumentException("Unexpected Gradle distribution: " + fileName);
        String release=fileName.substring(7,fileName.indexOf('-',7));
        String rootName="gradle-"+release;
        Path distBase=Paths.get(System.getProperty("user.home"),".gradle","wrapper","dists",fileName.replace(".zip",""),"kiwami");
        Path launcher=distBase.resolve(rootName).resolve("bin").resolve(isWindows()?"gradle.bat":"gradle");
        if(!Files.exists(launcher)) {
            Files.createDirectories(distBase);
            Path tempFile=distBase.resolve(fileName+".part");
            try {
                System.out.println("Downloading official Gradle distribution: " + distribution);
                download(distribution,tempFile);
                System.out.println("Extracting Gradle...");
                unzip(tempFile,distBase);
            } finally { Files.deleteIfExists(tempFile); }
        }
        if(!Files.isRegularFile(launcher)) throw new IOException("Gradle launcher not found: "+launcher);
        if(!isWindows())launcher.toFile().setExecutable(true);
        List<String> command=new ArrayList<>();
        if(isWindows()) {command.add("cmd.exe");command.add("/c");command.add(launcher.toString());}
        else {command.add("sh");command.add(launcher.toString());}
        Collections.addAll(command,args);
        Process process=new ProcessBuilder(command).directory(base).inheritIO().start();
        int exit=process.waitFor();
        if(exit!=0) System.exit(exit);
    }

    private static boolean isWindows(){return System.getProperty("os.name","").toLowerCase(Locale.ROOT).contains("win");}

    private static void download(String url,Path dest) throws IOException {
        URL current=new URL(url);
        for(int step=0;step<12;step++){
            HttpURLConnection c=(HttpURLConnection)current.openConnection();
            c.setInstanceFollowRedirects(false);
            c.setConnectTimeout(15000);
            c.setReadTimeout(60000);
            c.setRequestProperty("User-Agent","GradleWrapper/8.9");
            int status=c.getResponseCode();
            if(status==301||status==302||status==303||status==307||status==308){
                String next=c.getHeaderField("Location");c.disconnect();
                if(next==null)throw new IOException("Redirect without location");
                current=new URL(current,next);
                if(!"https".equalsIgnoreCase(current.getProtocol()))throw new IOException("Insecure redirect refused: "+current);
                continue;
            }
            if(status!=200)throw new IOException("Download failed, HTTP "+status+": "+current);
            try(InputStream in=new BufferedInputStream(c.getInputStream());OutputStream out=new BufferedOutputStream(Files.newOutputStream(dest))){
                byte[] buf=new byte[65536];int read;
                while((read=in.read(buf))!=-1)out.write(buf,0,read);
            }finally{c.disconnect();}
            return;
        }
        throw new IOException("Too many redirects");
    }

    private static void unzip(Path zip,Path dir) throws IOException {
        Path normalized=dir.toAbsolutePath().normalize();
        try(ZipInputStream in=new ZipInputStream(new BufferedInputStream(Files.newInputStream(zip)))){
            ZipEntry e;
            while((e=in.getNextEntry())!=null){
                Path target=normalized.resolve(e.getName()).normalize();
                if(!target.startsWith(normalized))throw new IOException("Unsafe ZIP path");
                if(e.isDirectory()){Files.createDirectories(target);}
                else{Files.createDirectories(target.getParent());Files.copy(in,target,StandardCopyOption.REPLACE_EXISTING);}
                in.closeEntry();
            }
        }
    }
}
