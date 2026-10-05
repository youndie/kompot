---
id: B-75
title: "Redis-шина проглатывает отказ в PSUBSCRIBE (#206)"
status: done
priority: P1
size: XS
stage: adoption-0.38
---

# B-75 — Redis-шина проглатывает отказ в PSUBSCRIBE

[Issue #206](https://github.com/youndie/kompot/issues/206), найдено при переводе kesh на 0.38.0.
`RedisKompotUpdateBus.messages()` отправлял свой единственный `PSUBSCRIBE` и выбрасывал future
ответа. Когда сервер подписку отклоняет — `NOAUTH` у Redis с паролем, ACL-пользователь без права
подписываться, прокси, не пропускающий pub/sub, — future завершается ошибкой, которую никто не
читает: поток остаётся открытым, ничего не выдаёт и никогда не падает. `KompotUpdateBroadcaster`
видит инстанс, которому просто никто не пишет, — то же молчаливое «обновления не приходят», что
[B-42](B-42-realtime-failure-is-swallowed.md) убрала на клиенте, только на сервере, куда B-42 не
дотянулась.

- **Решение — дождаться ответа на `PSUBSCRIBE` внутри `callbackFlow` до `awaitClose`.** Ошибка
  сервера тогда закрывает поток и доходит до сборщика с текстом сервера
  (`RedisCommandExecutionException: ERR …`). Очистка подписки переехала из блока `awaitClose` в
  `finally`: отклонённая подписка до `awaitClose` не доходит, и без этого её соединение
  переживало бы ошибку — по одному на каждую попытку.
- **Бродкастер не меняется, его поведение записано.** Сборщик шины — это `launch` в скоупе
  приложения, и ошибку он не ловит: она завершает `Job`, который вернул `start(scope)`, и уходит в
  скоуп как у любого упавшего `launch` — в его `CoroutineExceptionHandler` (или в
  uncaught-обработчик платформы) под `SupervisorJob`, отменой всего скоупа под обычным `Job`.
  Комментарий к `start()` это фиксирует, контракт `KompotUpdateBus.messages()` — тоже: отказ
  подписки роняет поток, а не оставляет его открытым.
- Отброшено: повторять подписку внутри шины. Отказ из-за пароля или прав сам не проходит, а цикл
  повторов снова сделал бы его тихим.
- Не делаем: `broadcast()` после падения сборщика по-прежнему публикует — другие инстансы
  доставляют, своим подписчикам не достаётся ничего, пока приложение не перезапустит бродкастер.
  Запрещать `broadcast()` по мёртвому сборщику значило бы ронять запросы и при штатной остановке
  скоупа; ошибка к этому моменту уже сказана один раз, громко.

- AC: шина над сервером, отвечающим на `PSUBSCRIBE` ошибкой, — `messages().collect` падает с
  `RedisCommandExecutionException`, в сообщении текст сервера; соединение подписки закрыто;
  бродкастер, запущенный в скоупе с `CoroutineExceptionHandler`, отдаёт ему ту же ошибку, а его
  `Job` отменён. Тест идёт в каждой сборке, без `REDIS_URL`.
- Якоря: `kompot-realtime-redis/src/main/kotlin/io/github/youndie/kompot/realtime/redis/RedisKompotUpdateBus.kt`,
  `kompot-realtime-redis/src/test/kotlin/io/github/youndie/kompot/realtime/redis/RefusedSubscriptionTest.kt`,
  `kompot-realtime-server/src/commonMain/kotlin/io/github/youndie/kompot/realtime/server/KompotUpdateBroadcaster.kt`.

## Находки

- **Тест — фальшивый RESP-сервер** (`RefusedSubscriptionTest`): `HELLO` отклоняется (Lettuce
  уходит на RESP2), `PSUBSCRIBE` — `-ERR control: PSUBSCRIBE refused`, остальное — `+OK`; сервер
  считает открытые соединения. Здесь подделка уместна, в отличие от `RedisKompotUpdateBusTest`:
  проверяется реакция кода на ответ с ошибкой, а не свойство Redis.
- **Контроль до правки** (новый тест на старой шине): `tests="3" failures="3"` — все три
  упираются в таймаут, `Expected an exception of class RedisCommandExecutionException to be thrown,
  but was TimeoutCancellationException: Timed out waiting for 5000 ms`. После правки — `tests="3"
  failures="0"`.
- **Настоящий Redis 7.2** с `--rename-command PSUBSCRIBE ""` (разовый тест, в репозиторий не
  вошёл): после правки `messages()` падает с `ERR unknown command 'PSUBSCRIBE', with args beginning
  with: 'kompot:updates:*'`; на старой шине — таймаут 5 с. Четыре теста `RedisKompotUpdateBusTest`
  на обычном Redis 7.2 (`REDIS_URL`) после правки зелёные: подписка, дождавшаяся ответа, доставляет
  как раньше.
- **kesh снимает обход** — `conformance/src/jvmMain/kotlin/io/github/youndie/kesh/conformance/Main.kt`,
  `kompotBus`: ветка `TimeoutCancellationException` с `psubscribeRefusal`. С исправленной шиной
  отказ приходит из `messages()` и попадает в общую ветку `catch (e: Exception)`; его `KompotBusTest`
  краснеет — это и есть сигнал, как сказано в issue.
