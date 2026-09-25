---
id: B-65
title: "Тест Redis-шины «с одного инстанса на другой» не запускает бродкастер отправителя"
status: wip
priority: P2
size: XS
---

# B-65 — Тест Redis-шины «с одного инстанса на другой» не запускает бродкастер отправителя

`RedisKompotUpdateBusTest` — «an update published on one instance reaches a subscriber on another» —
падает на настоящем Redis 7.2 (и на kesh):

```
java.lang.IllegalStateException: KompotUpdateBroadcaster.start(scope) was never called — the publish
reaches the bus, but nothing will deliver it
    at ...KompotUpdateBroadcaster.broadcast(KompotUpdateBroadcaster.kt:74)
```

Тест публикует через `KompotUpdateBroadcaster(instanceA).broadcast(...)`, не вызвав у него
`start(scope)`. Бродкастер ровно это и запрещает: `broadcast()` без запущенного сборщика шины
доходит до шины, но на своём инстансе не доставляется никому, и молчаливый симптом «обновления не
приходят» на причину не указывает. Остальные три теста файла проходят. CI этот файл не прогоняет
(без `REDIS_URL` тесты пропускаются), поэтому поломку не заметили.

- **Решение — чинить тест, а не проверку.** Инстанс A в тесте — это приложение, а приложение
  стартует свой бродкастер при запуске; тест, который так не делает, моделирует конфигурацию,
  которую проверка и призвана запретить. Бродкастер A запускается так же, как B.
- Отброшено: публиковать прямо в шину (`instanceA.publish`). Это прошло бы, но проверяло бы уже
  шину, а не путь «запрос на A → стрим на B» через `broadcast()`, который тест называет;
  шину саму по себе уже держит первый тест файла.
- Не делаем: не трогаем `check(started)` и не включаем прогон с Redis в CI — это отдельное решение
  об инфраструктуре CI.

- AC: `REDIS_URL=… ./gradlew :kompot-realtime-redis:test --rerun-tasks` на настоящем Redis 7.2 —
  четыре теста проходят, в JUnit XML `tests="4" skipped="0" failures="0" errors="0"`; подписчик на
  B получает сообщение, опубликованное через `broadcast()` на A, два разных `RedisClient`.
- Якоря: `kompot-realtime-redis/src/test/kotlin/io/github/youndie/kompot/realtime/redis/RedisKompotUpdateBusTest.kt`,
  `kompot-realtime-server/src/commonMain/kotlin/io/github/youndie/kompot/realtime/server/KompotUpdateBroadcaster.kt`.
