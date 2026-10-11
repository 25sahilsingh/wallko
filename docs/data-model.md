# Data model (Room)

← [Back to the plan index](PLAN.md)

The tables stored on the phone.

```
PlantSpecies      (bundled, read-only)   index PK, latin, common, family, type, description,
                                          observations, rarity, thirst, sun, growth, bloom[],
                                          badges[], sources{}, dbVersion
OwnedPlant                               id PK, speciesIndex FK, foundAt, foundLat/Lng, placeName,
                                          photoPath, spritePath?, styleId, plantedAt,
                                          lastDrankAt, state (SEEDLING|GROWING|GROWN|WILTING|WITHERED),
                                          gardenX, gardenY, onShelf
Wallet                                   singleton: waterDrops, elixir, tankCapacity, lastTickAt
LedgerEntry                              id, at, kind (STEPS|WALK_DISTANCE|NEW_CELL|DAWN_BONUS|
                                          DRINK|REVIVE|STARTER), drops, elixir, note
Walk                                     id, startedAt, endedAt, distanceM, steps, newCells,
                                          dropsEarned, elixirEarned, dawn
WalkPoint                                walkId FK, at, lat, lng
ExploredCell                             cellId PK, firstVisitedAt
StepDay                                  date PK, steps, dropsCredited
```

Species JSON → Room seeding happens on first launch and whenever `dbVersion` in the bundled JSON is newer than the stored one. Owned plants reference species by index, so reseeding never breaks a collection.
