package fr.neamar.kiss.loader;

import androidx.annotation.NonNull;

import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import fr.neamar.kiss.dataprovider.ReloadFilter;
import fr.neamar.kiss.pojo.Pojo;
import fr.neamar.kiss.pojo.PojoWithPackageName;
import fr.neamar.kiss.utils.Log;

public class PackageNameFilter implements ReloadFilter {

    private static final String TAG = PackageNameFilter.class.getSimpleName();

    private final Set<String> packageNames;

    public PackageNameFilter(String[] packageNames) {
        this(Set.of(packageNames));
    }

    public PackageNameFilter(String packageName) {
        this(Set.of(packageName));
    }

    private PackageNameFilter(@NonNull Set<String> packageNames) {
        this.packageNames = packageNames;
    }

    public Set<String> getPackageNames() {
        return this.packageNames;
    }

    public <T extends Pojo> List<T> merge(List<T> existingPojos, List<T> newPojos) {
        List<T> merged = Stream.concat(existingPojos.stream().filter(this::canKeep), newPojos.stream()).collect(Collectors.toList());
        Log.d(TAG, "Merged " + newPojos.size() + "pojos into " + existingPojos.size() + " pojos, resulting in " + merged.size() + " pojos.");
        return merged;
    }

    public <T> List<T> execute(Function<String, List<T>> fn) {
        return packageNames.stream()
                .map(fn)
                .flatMap(Collection::stream)
                .collect(Collectors.toList());
    }

    private <T extends Pojo> boolean canKeep(T pojo) {
        if (pojo instanceof PojoWithPackageName) {
            return !packageNames.contains(((PojoWithPackageName) pojo).getPackageName());
        } else {
            return true;
        }
    }
}
