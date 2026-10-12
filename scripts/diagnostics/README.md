# Диагностика этапа 1

`RenderDiagnostics` и ветвления `GuiController` отключены по умолчанию.
Включение: `-Dcgengine.diagnostics=true`. Режим задаётся через
`-Dcgengine.diagnostics.mode=normal|cached|no-upload`.

- `normal`: текущий цикл AnimationTimer, CPU render и Canvas upload только при
  изменении сцены. До этапа 8 использовался Timeline 15 мс с непрерывным render.
- `cached`: CPU-кадр рассчитывается при первом появлении/смене набора моделей
  или размеров Canvas; при остальных изменениях публикуется тот же framebuffer. Цвет и камера
  меняют состояние UI, но опубликованная геометрия остаётся прежней — это
  диагностическое сравнение, не рабочий режим просмотра.
- `no-upload`: тот же CPU render, без PixelWriter/Canvas upload.
- `minimal`: отдельное окно `Stage1Probe`, без GuiController, renderer и моделей,
  с текстовым полем, настоящим file chooser и источником native drag-and-drop.

После этапа 8 dirty-проверка применяется к трём режимам полного окна:
неподвижная сцена не пересчитывается и не публикуется повторно. Для повторения
исходной диагностики непрерывного Timeline использовать сохранённый baseline JAR.

`Stage1Probe.java` находится вне `tests/` и не запускается обязательным CI.
Автоматический сценарий создаёт настоящий FXML/Stage и оставляет цикл кадров
работать. Он ставит действия в очередь из другого потока, вызывает настоящие
обработчики меню/кнопок и события JavaFX. Для автоматического load подставляется
путь вместо file chooser. Это отдельная проверка от настоящего ввода Windows.
`--manual` отключает автоматические действия; `-Dcgengine.diagnostics.nativeDialogs=true`
возвращает настоящие диалоги полного окна. `--multi` добавляет второй OBJ.

Измеряются независимо CPU render, Canvas upload, обработчик кадра, интервал
AnimationTimer pulse, очередь UI, очередь/обработчик действия и время до
следующего pulse. Последнее не является измерением физической презентации
пикселей на мониторе. Длительность модального диалога не является задержкой WASD.
Длительность CPU render измеряется по monotonic wall clock вокруг метода,
а не через счётчик CPU-time потока; фактическая активность/ожидание проверяется
отдельно thread dump и JFR.
Имена методов и данные камеры, Canvas, модели, PID и UTC timestamps есть в логе.

Каждые две секунды публикуются агрегаты и события от 500 мс. На очереди UI
держится не более одного heartbeat; это выборка задержки очереди, а не очередь
из сотен синтетических событий. При ожидании heartbeat более секунды watchdog
снимает три `jcmd Thread.print -l` с интервалом около секунды после завершения
предыдущей команды; максимум три такие серии на запуск. Таймаут самого jcmd
пять секунд и ошибки attach явно сохраняются. Диагностическая JVM завершается
с кодом 2 при ошибке/таймауте, автоматический сценарий ограничен 60 секундами.
Не считать незавершённый сценарий прошедшим.

Повторение из корня проекта, в новом каталоге:

```powershell
mvn '-Dtest=ObjIoTest,ModelValidatorTest,LightTest,CameraAndConveyorTest,ModelFramingTest,UiRegressionTest' package
New-Item -ItemType Directory -Force AI_dev/stage1-repeat-001/probe | Out-Null
javac -encoding UTF-8 -cp target/cgengine.jar -d AI_dev/stage1-repeat-001/probe scripts/diagnostics/Stage1Probe.java
./scripts/diagnostics/run-stage1.ps1 -OutputDirectory AI_dev/stage1-repeat-001
./scripts/diagnostics/summarize-stage1.ps1 -Directory AI_dev/stage1-repeat-001
```

Скрипт не перезаписывает существующие каталоги запусков. Каждый режим/модель
запускается в новой JVM. Перед пересборкой сохранять нужные JAR и аварийные
журналы. Архивные модели остаются локальными: их отсутствие означает отсутствие
проверки соответствующего сценария. Для CLI использовать относительные ASCII
пути вывода JFR/hs_err: нативные startup options JDK 21.0.10 на этой машине
неверно обработали абсолютный путь с кириллицей.

Пример минимального нативного сценария (после создания каталога `minimal`):

```powershell
java '-Dcgengine.diagnostics=true' '-Dcgengine.diagnostics.mode=minimal' '-XX:ErrorFile=AI_dev/stage1-repeat-001/minimal/hs_err_pid%p.log' -cp 'AI_dev/stage1-repeat-001/probe;target/cgengine.jar' com.cgengine.Stage1Probe Test_Models/cube.obj AI_dev/stage1-repeat-001/minimal --manual
```

Открыть/отменить chooser, перетащить подпись в поле, выполнить Ctrl+A/C/V,
закрыть окно. Для полного окна заменить `minimal` на `normal` либо `cached`
и добавить `-Dcgengine.diagnostics.nativeDialogs=true`.

Сводка объединяет количества, взвешенные средние интервалов и наблюдаемые
максимумы. Процентили отдельных интервалов нельзя превращать в общий p95.
При принудительном завершении без финального flush максимум учитывает также
записанные slow-события, среднее относится только к сброшенным агрегатам.
