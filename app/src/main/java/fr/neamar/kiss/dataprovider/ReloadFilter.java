package fr.neamar.kiss.dataprovider;

import java.util.List;

import fr.neamar.kiss.pojo.Pojo;

public interface ReloadFilter {
    <T extends Pojo> List<T> merge(List<T> existingPojos, List<T> newPojos);
}
