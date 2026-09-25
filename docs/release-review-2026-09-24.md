<sub>ssheleg skills — task-pipeline · evidence-docs · quest-store · quest-tooling · ux-scenarios · brand-voice · copywriting</sub>

# Nicegram VR: кандидат для ревью, 24 сентября 2026

Продолжение 25 сентября: [новая политика CI, интеграция и оставшиеся шаги](release/2026-09-25-resume.md).

Статус: **подписанный кандидат собран, проверка APK — PASS**. Артефакты сохранены в
[черновике релиза](https://github.com/nicegram/Nicegram-VR/releases/tag/untagged-3d8b3add3d59711c72fe)
(нужен доступ к репозиторию). Это ещё не аппаратно принятый билд: Quest не подключён.
В Horizon Store этот прогон ничего не отправлял; публичный релиз не опубликован.

| Параметр | Проверенное значение |
|---|---|
| APK | nicegram-vr-v0.1.0-rc.1.apk; 60,662,072 bytes |
| Версия / code | 0.1.0 (Telegram 12.10.3) / 7089049 |
| Package / ABI | my.nicegram.vr / arm64-v8a |
| SDK | min 29, target 34, compile 36 |
| Подпись | v2; сертификат проекта, SHA-256 в receipt |
| Разрешения | 57; совпадений с 117 запрещёнными — 0 |
| Исходники APK | `14935de99c0edc3620d6754a69a5ca553dbb0aa5` |
| SHA-256 APK | `11ef5e258595fa2711c8771b5779919b5348e95d8e0e0c0d38d91680b4d2a70a` |

[JSON проверки APK](release/2026-09-24-apk.json) ·
[Результаты тестов](release/2026-09-24-checks.json) ·
[Текст черновика релиза](release/2026-09-24-notes.md).
Сборка завершилась за 2m 46s, exit 0. Контрольная сумма загруженного GitHub asset совпала
с локальной. Валидатор после компиляции дополнен проверкой имён permissions без подчёркивания
(`DUMP`, `REBOOT`, `DIAGNOSTIC`); эти разрешения APK не запрашивает. Ревизия валидатора:
`7d2a16823af230c715d18c444872a6a533b185d9`. Исполняемый код APK после сборки не менялся.

## Объём проверки

Сравнение с импортированным деревом Telegram `9552e5541e1274b9557c9832b204dbfcaf44b3dc`:
проверены изменения в точках уведомлений, звонков, масштаба, брендинга и композера,
а также собственные диктовка/дайджест, Gradle и release workflow. Это ограниченная проверка
добавленного слоя, не аудит всего Telegram и его нативных зависимостей.
Исправления кода: `5d786506`; совместимый сетевой тест: `7896933c`; target SDK и проверка AAPT: `14935de9`.
[Бриф и границы](release/2026-09-24-brief.md). Старый alpha.3 не подходит для этого ревью.

## Подтверждённые находки

| ID | Ошибка и исправление | Проверка |
|---|---|---|
| A-45 | По лимиту флаг записи сбрасывался до callback, и UI терял фразу. stop ждал поток под его же блокировкой; ошибка read могла бесконечно удерживать микрофон. Поток теперь освобождает ресурс, сохраняет аудио до чтения и завершается без общей блокировки. UI останавливает запись вне основного потока и отбрасывает поздний результат после ухода. | VoiceRecorderLifecycleTest: лимит, read error, stop, start failure. Живой микрофон и UI пока NOT-RUN. |
| A-46 | Один дайджест объединял одинаковые chat id разных аккаунтов; повтор доставки удваивал счётчик. Данные разделены по аккаунтам/владельцам, недавние message id дедуплицируются, snapshot копируется. | DigestTest: account isolation, reconnect, slot reuse, stable snapshot. Дедупликация ограничена 10 000 последними ID на аккаунт; это не постоянный архив. |
| A-47 | Release workflow мог пропустить policy/signature check и объявить успех; ручной запуск использовал имя ветки вместо выбранного тега. Проверки теперь завершаются ошибкой при недостаточных данных, проверяют сертификат, версию, ABI, размер и манифест. Используется один RELEASE_TAG. | Tools/tests/test_release.py; проверка реального APK; Android SDK в CI закреплён явно. Новый workflow ещё не запускался на GitHub. |
| A-48 | В старом APK у LaunchActivity не было excludeFromRecents, хотя документ утверждал обратное. Манифест также объявлял Quest 2/Pro, не входящие в заявленный продукт. Добавлен атрибут, список устройств ограничен Quest 3/3S. Target SDK нового Store-приложения установлен в 34 при compile SDK 36. | aapt2 dump xmltree; Tools/check_release.py проверяет конкретный узел activity. |
| A-49 | HTTP-хост 127.speech.example.com считался loopback из-за startsWith. Теперь допустим только числовой IPv4 127/8 или localhost/IPv6 loopback; HTTP redirects не выполняются. | SpeechSettingsTest упал на прежнем коде; HttpSpeechToTextTest проверяет настоящий HTTP-запрос на loopback и отсутствие запроса по redirect. |
| A-50 | README, production setup, описание VR-слоя и Dataroom противоречили текущему коду; store form содержала старые privacy/terms и дубль поля. Текущие утверждения исправлены, история сохранена. | check_docs, brand lint, ручное сопоставление; privacy/terms HTTP 200. |

## Проверки и пределы доказательств

- Базовый прогон: 86 JVM-тестов, 19 классов, ошибок нет.
- После исправлений: 97 JVM-тестов, 21 класс, ошибок нет; Gradle exit 0. При финальной
  смене только target SDK Gradle повторно использовал этот результат (`UP-TO-DATE`).
- Повторное введение прежних механизмов вызвало три падения: смешение аккаунтов,
  потеря фразы на лимите и stop под общей блокировкой. После восстановления фиксов тесты проходят.
- Отдельный тест HTTP-домена с префиксом 127 упал до исправления и прошёл после.
- Восемь Python-тестов release validation проходят, включая негативные варианты сертификата,
  отсутствующего policy list, ABI, размера, версии, debuggable, target SDK, атрибута чужой activity и permissions без подчёркивания.
- `adb devices -l`: подключённых устройств нет. Проверки реальных звонков, речи, сна,
  восстановления аккаунта, кадров и удобства управления не выполнялись.
- Java 8 source/target и Gradle deprecated API дают предупреждения upstream. Это не ошибки
  текущей компиляции; переход на Gradle 9 не входит в этот кандидат.
- Тесты рекордера используют fake audio input; сетевые тесты используют loopback, без
  внешнего сервиса и реального аудио. Они не измеряют качество распознавания.

## Воспроизведение

Исходный код кандидата: `14935de99c0edc3620d6754a69a5ca553dbb0aa5`.
Нужны JDK 21, Android SDK/NDK из Gradle, закреплённые submodules и переменные из
[production setup](production-setup.md). Значения ключей не входят в Git.

```sh
./gradlew --no-daemon -PquestAbiOnly :TMessagesProj_AppQuest:assembleQuestStandalone :TMessagesProj_AppQuest:testQuestDebugUnitTest
python3 -m unittest discover -s Tools/tests -v
python3 Tools/check_docs.py
```

`Tools/check_release.py --help` описывает проверку готового APK: передать свежую страницу
запрещённых permissions, сертификат из receipt, тег `v0.1.0-rc.1` и предыдущий code
`7089039`. Перед новым Store upload сверить максимальный code в Dashboard; локальная
проверка сравнивает только с известным предыдущим кандидатом. Локальный build использовал
существующий vault wrapper; его машинные пути и вывод остаются вне Git.

## Чеклист владельцу: идти сверху вниз

1. **APK и идентичность.** Скачать кандидат и JSON receipt, сверить SHA-256, versionCode и
   сертификат. Устанавливать именно standalone APK; старый alpha.3 не использовать.
2. **Quest 3 и 3S.** Пройти [device-session](device-session.md) на этом APK: холодный запуск,
   вход, текст/медиа, тишина и разрешённые звонки, отдельный телефон с тем же аккаунтом,
   диктовка/отказ микрофона/лимит/уход/сон, два аккаунта, обновление без потери данных,
   четыре масштаба и длительный прогон. Записать PASS/FAIL по пунктам; сейчас NOT-RUN.
3. **Карточка Meta.** Подтвердить организацию и приложение, package id `my.nicegram.vr`,
   дату создания и максимальный уже загруженный versionCode. Доступен локальный metavr login,
   но нужные organisation/app id и состояние Dashboard в этом прогоне не установлены.
4. **Политика и данные.** Закрыть VRQ-001 (клиент стороннего мессенджера) и VRQ-002
   (предложение телефонного приложения) из [подготовленного письма](../store/meta-policy-letter.md).
   Письмо не отправлено. Проверить применимость Data Use Checkup, заполнить возрастную анкету
   по реальному UGC и выбрать страны; проверить отдельные требования для Южной Кореи.
5. **Материалы.** Использовать [исправленные поля](../store/listing-en.md), действующие
   privacy/terms и support@appvillis.com. Сделать реальные кадры с тестовыми чатами на этом
   билде по [assets-checklist](../store/assets-checklist.md). Не публиковать личную переписку.
   Не заявлять русскую локализацию до загрузки и проверки пакета.
6. **Тестовый канал.** После предыдущих проверок загрузить в ALPHA или RC, явно добавить
   тестеров, установить из Library и повторить cold launch/update. Успешный sideload не
   доказывает установку из канала. Сейчас NOT-UPLOADED.
7. **Самопроверка VRC.** Отметить применимые строки для 2D-приложения PASS/FAIL/NOT-RUN/N/A,
   приложить evidence на точный APK. Проверить предупреждения о разрешениях при upload;
   ноль запрещённых не означает, что остальные не требуют review.
8. **Отправка.** Выбрать проверенный build в Production, заполнить submission info и reviewer
   instructions, затем Submit for Review. Зафиксировать build id и статус. Загрузка сама
   по себе ревью не запускает. Сейчас NOT-SUBMITTED.
9. **После ответа.** Исправить замечания на том же signing key, увеличить versionCode,
   повторить затронутые проверки. Публикацию планировать после одобрения; старый alpha.3
   не является безопасным вариантом отката.

Актуальные источники Meta прочитаны 24 сентября 2026:
[манифест](https://developers.meta.com/horizon/resources/publish-mobile-manifest/),
[запрещённые разрешения](https://developers.meta.com/horizon/resources/permissions-prohibited/),
[каналы](https://developers.meta.com/horizon/resources/publish-release-channels/),
[ревью и DUC](https://developers.meta.com/horizon/resources/publish-app-review/),
[VRC](https://developers.meta.com/horizon/resources/publish-quest-req/).
Применимость политик к конкретному приложению и ответы Dashboard не подменены чтением страниц.

## Handoff

Следующая задача: проверить этот подписанный кандидат на подключённом Quest и закрыть
пункты device-session. После этого владелец приложения проходит Dashboard-пункты выше.
Не менять ветку на main и не публиковать тег автоматически. Секреты, keystore, локальные
логи и Android build caches остаются вне Git. Центральный межрепозиторный индекс — Dataroom,
[папка Nicegram VR](https://github.com/nicegram/dataroom/tree/main/docs/workspaces/nicegram-vr); private workspace хранит канонические сценарии.

Humanization: on, own pass. Исправлены неверные обещания и повторяющиеся поля; новые
маркетинговые показатели не добавлены. Brand lint проходит; аппаратная готовность не заявлена.

**Made with [ssheleg skills](https://github.com/ssheleg/sshlg-skills)**

- task-pipeline — review and signed candidate workflow; evidence-docs — source receipts and handoff.
- quest-store — live packaging and publication requirements; quest-tooling — toolchain and device inventory.
- ux-scenarios — account and dictation acceptance; brand-voice — record existing store voice;
  copywriting — correct store description.
