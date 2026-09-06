package me.devtec.shared.dataholder;

import org.junit.*;
import static org.junit.Assert.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import me.devtec.shared.dataholder.store.*;
import me.devtec.shared.dataholder.loaders.*;

public class ConfigRewriteTest {
    private Path temp;
    @Before public void setup() throws Exception {temp=Files.createTempDirectory("config-tests-");TempStoragePolicy.directory=temp.toFile();ConfigMemoryPolicy.maxMemoryBytes=8*1024*1024;}
    @After public void cleanup() throws Exception {ConfigMemoryPolicy.maxMemoryBytes=0;ConfigMemoryPolicy.globalMemoryBytes=0;TempStoragePolicy.directory=new File(System.getProperty("java.io.tmpdir"));try(java.util.stream.Stream<Path> files=Files.walk(temp)){files.sorted(Comparator.reverseOrder()).forEach(p->{try{Files.deleteIfExists(p);}catch(IOException ignored){}});}}
    @Test public void compatibility() throws Exception {YamlCompatibilitySuite.main(new String[0]);}
    @Test public void typedApiAndLifecycle(){try(Config c=new Config()){
        assertFalse(c.isModified());assertSame(c,c.set("a.b",12));assertTrue(c.isModified());assertTrue(c.isKey("a"));assertFalse(c.existsKey("a"));assertTrue(c.existsKey("a.b"));assertEquals(12,c.getLong("a.b"));assertEquals("12",c.getString("a.b"));assertFalse(c.setIfAbsent("a.b",13));assertTrue(c.setIfAbsent("a.c",true));assertTrue(c.getBoolean("a.c"));assertEquals(45,c.getInt("missing",45));assertEquals(Arrays.asList("b","c"),new ArrayList<String>(c.getKeys("a")));c.remove("a");assertTrue(c.getKeys(true).isEmpty());c.close();c.set("reused",1);assertEquals(1,c.getInt("reused"));
    }}
    @Test public void collectionSemantics(){try(Config c=new Config()){c.set("list",new ArrayList<Object>(Arrays.<Object>asList(1,2)));((List<Object>)c.get("list")).add(3);assertEquals(3,c.getList("list").size());c.getList("list").clear();assertEquals(3,c.getList("list").size());}}
    @Test public void nestedJson(){try(Config c=new Config()){c.set("a.b.c",7);c.set("a.b.d",8);assertEquals("{\"a\":{\"b\":{\"c\":7,\"d\":8}}}",c.toString(DataType.JSON));try(Config r=Config.loadFromString(c.toString(DataType.JSON))){assertEquals(7,r.getInt("a.b.c"));}}}
    @Test public void dottedJsonAndSectionWins(){try(Config c=Config.loadFromString("{\"foo.bar\":123}")){assertEquals(123,c.getInt("foo.bar"));c.set("foo",1);assertEquals("{\"foo\":{\"bar\":123}}",c.toString(DataType.JSON));}}
    @Test public void byteGolden(){try(Config c=new Config()){c.set("a",12);String bytes=c.toString(DataType.BYTE);assertEquals("AQFhAjEy",bytes);try(Config r=Config.loadFromString(bytes)){assertEquals(12,r.getInt("a"));}}}
    @Test public void transactionalReload(){try(Config c=new Config()){c.set("kept",1);c.reload("{\"bad\":");assertEquals(1,c.getInt("kept"));assertNotNull(DataLoader.lastLoadError);c.reload(new ByteArrayInputStream("{bad".getBytes(StandardCharsets.UTF_8)));assertEquals(1,c.getInt("kept"));}}
    @Test public void saveFailureKeepsTarget() throws Exception {Path file=temp.resolve("target.json");Files.write(file,"ORIGINAL".getBytes(StandardCharsets.UTF_8));try(Config c=new Config(file.toFile(),false)){c.set("bad",Double.NaN);c.save(DataType.JSON);assertEquals("ORIGINAL",new String(Files.readAllBytes(file),StandardCharsets.UTF_8));assertTrue(c.isModified());}try(java.util.stream.Stream<Path> files=Files.list(temp)){assertEquals(1,files.count());}}
    @Test public void diskAndMigrationEquivalence(){try(Config c=new Config()){
        for(int i=0;i<500;i++)c.set("section"+(i%9)+".key"+i,i);c.setComments("section2.key2",Arrays.asList("# hi"));c.setHeader(Arrays.asList("# header"));c.setFooter(Arrays.asList("# footer"));String before=c.toString(DataType.JSON),yaml=c.toString(DataType.YAML);List<String> order=new ArrayList<String>(c.getKeys(true));
        for(int round=0;round<2;round++){c.getDataLoader().document().storage.forceDisk();assertEquals(before,c.toString(DataType.JSON));assertEquals(yaml,c.toString(DataType.YAML));assertEquals(order,new ArrayList<String>(c.getKeys(true)));for(int i=0;i<500;i++)assertEquals(i,c.getInt("section"+(i%9)+".key"+i));c.getDataLoader().document().storage.forceMemory();assertEquals(before,c.toString(DataType.JSON));}
    }}
    @Test public void randomCrossStore(){try(Config a=new Config();Config b=new Config()){
        b.getDataLoader().document().storage.forceDisk();Random random=new Random(827361);
        for(int i=0;i<1600;i++){String key="r"+random.nextInt(12)+".s"+random.nextInt(8)+".k"+random.nextInt(20);if(random.nextInt(4)==0){a.remove(key);b.remove(key);}else{int value=random.nextInt();a.set(key,value);b.set(key,value);}if(i%100==0){assertEquals(a.toString(DataType.JSON),b.toString(DataType.JSON));assertEquals(a.getKeys(true),b.getKeys(true));}}
    }}
    @Test public void dirtyEpoch(){try(Config c=new Config()){c.set("a",1);c.getDataLoader().document().storage.forceDisk();assertTrue(c.getDataLoader().get("a").modified);c.toString("json",true);assertFalse(c.getDataLoader().get("a").modified);c.set("a",2);assertTrue(c.getDataLoader().get("a").modified);}}
    @Test public void cloneIndependent(){try(Config a=new Config()){a.set("a",new ArrayList<Object>(Arrays.<Object>asList(1,2)));try(Config b=new Config(a)){((List<Object>)b.get("a")).add(3);assertEquals(2,a.getList("a").size());}a.getDataLoader().document().storage.forceDisk();try(Config b=new Config(a)){b.set("b",2);assertFalse(a.exists("b"));}}}
    @Test public void mergePersistsInDisk(){try(Config a=new Config();Config b=new Config()){a.getDataLoader().document().storage.forceDisk();b.set("a",12);b.setComments("a",Arrays.asList("# comment"));a.merge(b);assertEquals(12,a.getInt("a"));assertEquals(Arrays.asList("# comment"),a.getComments("a"));}}
    @Test public void diskFullMigrationRollback() throws Exception {try(Config c=new Config()){c.set("a",1);Path unusable=temp.resolve("file");Files.write(unusable,new byte[]{1});TempStoragePolicy.directory=unusable.toFile();try{c.getDataLoader().document().storage.forceDisk();fail();}catch(IllegalStateException expected){}assertEquals(1,c.getInt("a"));assertEquals(AdaptiveConfigStore.State.MEMORY,c.getDataLoader().document().storage.state());}}
    @Test public void randomRoundTrips(){Random r=new Random(662);for(int round=0;round<20;round++)try(Config c=new Config()){for(int i=0;i<80;i++)c.set("root"+r.nextInt(8)+".ž"+i,r.nextBoolean()?Integer.valueOf(r.nextInt()):"text 😀 "+i);for(DataType type:new DataType[]{DataType.JSON,DataType.YAML})try(Config d=Config.loadFromString(c.toString(type))){for(String k:c.getKeys(true))assertEquals(k,c.get(k),d.get(k));}}}
    @Test public void callbackRegistration(){try(Config c=new Config()){Runnable callback=()->{};assertSame(c,c.addRunnableOnReload(callback));assertSame(c,c.removeRunnableOnReload(callback));}}
    @Test public void nodeRemoval(){try(Config c=new Config()){c.set("a.x.y",1);c.set("a.x.z",2);c.set("a.b",3);c.set("a",4);c.remove("a.x");assertFalse(c.isKey("a.x"));assertEquals(3,c.getInt("a.b"));assertEquals(4,c.getInt("a"));c.set("a.x.y",5);assertEquals(5,c.getInt("a.x.y"));}}
    @Test public void spillDuringLoad(){long budget=ConfigMemoryPolicy.maxMemoryBytes;ConfigMemoryPolicy.maxMemoryBytes=65536;StringContainer input=new StringContainer("root:\n");for(int i=0;i<1000;i++)input.append("  k").append(i).append(": value").append(i).append('\n');try(Config c=Config.loadFromString(input.toString())){assertEquals(1000,c.getKeys("root").size());assertEquals("value999",c.getString("root.k999"));assertTrue(c.getDataLoader().document().storage.store().disk());}finally{ConfigMemoryPolicy.maxMemoryBytes=budget;}}
}
