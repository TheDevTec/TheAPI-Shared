package me.devtec.shared.dataholder.loaders.constructor;
import me.devtec.shared.dataholder.loaders.DataLoader;
public interface DataLoaderConstructor {
    DataLoader construct();
    String name();
    default boolean isConstructorOf(String t){ return name().equalsIgnoreCase(t); }
    default boolean supportsLargeFiles(){ return false; }
}
