package dev.rightknight.service;

import dev.rightknight.model.AppUserEntity;

import java.time.ZonedDateTime;

public interface GameImportService {

    int importGames(AppUserEntity appUser, ZonedDateTime from, ZonedDateTime until);
}
