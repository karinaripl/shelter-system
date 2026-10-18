package com.shelter.core.mapper;

/** Ссылки на файлы фото (относительно шлюза). */
public final class PhotoUrls {

    private PhotoUrls() {
    }

    /** Для сотрудников: любое фото любого животного. */
    public static String staff(Long animalId, Long photoId) {
        return photoId == null ? null : "/animals/" + animalId + "/photos/" + photoId + "/file";
    }

    /** Для публичного каталога: отдаётся, только пока животное есть в каталоге. */
    public static String publicUrl(Long photoId) {
        return photoId == null ? null : "/public/photos/" + photoId;
    }
}
