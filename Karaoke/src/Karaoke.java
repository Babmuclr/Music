import java.lang.invoke.MethodHandles;

import static jp.ac.kyoto_u.kuis.le4music.Le4MusicUtils.verbose;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Observable;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Stream;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.imageio.ImageIO;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.beans.value.ObservableValue;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.stage.Stage;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.control.Label;
import javafx.scene.control.Slider;
import javafx.scene.image.WritableImage;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.embed.swing.SwingFXUtils;
import javafx.geometry.Pos;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.HelpFormatter;

import org.apache.commons.math3.complex.Complex;
import org.apache.commons.math3.util.MathArrays;

import com.sun.javafx.util.Utils;

import jp.ac.kyoto_u.kuis.le4music.Le4MusicUtils;
import jp.ac.kyoto_u.kuis.le4music.LineChartWithSpectrogram;
import jp.ac.kyoto_u.kuis.le4music.Player;
import jp.ac.kyoto_u.kuis.le4music.Recorder;

import java.io.IOException;
import javax.sound.sampled.UnsupportedAudioFileException;
import org.apache.commons.cli.ParseException;

public final class Karaoke extends Application {

	private static final Options options = new Options();
	private static final String helpMessage =
	MethodHandles.lookup().lookupClass().getName() + " [OPTIONS]";

	static {
	    /* コマンドラインオプション定義 */
	    options.addOption("h", "help", false, "Display this help and exit");
	    options.addOption("v", "verbose", false, "Verbose output");
	    options.addOption("m", "mixer", true,
	                      "Index of Mixer object that supplies a SourceDataLine object. " +
	                      "To check the proper index, use CheckAudioSystem");
	    options.addOption("o", "outfile", true, "Output file");
	    options.addOption("r", "rate", true, "Sampling rate [Hz]");
	    options.addOption("f", "frame", true, "Frame duration [seconds]");
	    options.addOption("i", "interval", true, "Frame update interval [seconds]");
	}

	public final void start(final Stage primaryStage)
			  throws IOException,
			         UnsupportedAudioFileException,
			         LineUnavailableException,
			         ParseException {
		final String[] args = getParameters().getRaw().toArray(new String[0]);
		final CommandLine cmd = new DefaultParser().parse(options, args);
	    if (cmd.hasOption("help")) {
	      new HelpFormatter().printHelp(helpMessage, options);
	      Platform.exit();
	      return;
	    }
	    verbose = cmd.hasOption("verbose");
	    final String[] pargs = cmd.getArgs();
	    if (pargs.length < 1) {
	      System.out.println("WAVFILE is not given.");
	      new HelpFormatter().printHelp(helpMessage, options);
	      Platform.exit();
	      return;
	    }
	    final File wavFile = new File(pargs[0]);

	    /* 定数の定義 */
	    final double duration =
	    	      Optional.ofNullable(cmd.getOptionValue("duration"))
	    	        .map(Double::parseDouble)
	    	        .orElse(Le4MusicUtils.spectrogramDuration);

	    final double interval =
	    	      Optional.ofNullable(cmd.getOptionValue("interval"))
	    	        .map(Double::parseDouble)
	    	        .orElse(Le4MusicUtils.frameInterval);

	    final double frameDuration =
	    	      Optional.ofNullable(cmd.getOptionValue("frame"))
	    	        .map(Double::parseDouble)
	    	        .orElse(Le4MusicUtils.frameDuration);

	    final Player.Builder playbuilder = Player.builder(wavFile);
	    final Recorder.Builder recbuilder = Recorder.builder();

	    Optional.ofNullable(cmd.getOptionValue("rate"))
	      .map(Float::parseFloat)
	      .ifPresent(recbuilder::sampleRate);

	    Optional.ofNullable(cmd.getOptionValue("mixer"))
	      .map(Integer::parseInt)
	      .map(index -> AudioSystem.getMixerInfo()[index])
	      .ifPresent(playbuilder::mixer);
	    Optional.ofNullable(cmd.getOptionValue("mixer"))
	      .map(Integer::parseInt)
	      .map(index -> AudioSystem.getMixerInfo()[index])
	      .ifPresent(recbuilder::mixer);

	    if (cmd.hasOption("loop"))
	        playbuilder.loop();

	    Optional.ofNullable(cmd.getOptionValue("buffer"))
	      .map(Double::parseDouble)
	      .ifPresent(playbuilder::bufferDuration);

	    Optional.ofNullable(cmd.getOptionValue("frame"))
	      .map(Double::parseDouble)
	      .ifPresent(playbuilder::frameDuration);

	    Optional.ofNullable(cmd.getOptionValue("outfile"))
	      .map(File::new)
	      .ifPresent(recbuilder::wavFile);
	    recbuilder.frameDuration(frameDuration);

	    Optional.ofNullable(cmd.getOptionValue("interval"))
	      .map(Double::parseDouble)
	      .ifPresent(recbuilder::interval);

	    playbuilder.interval(interval);

	    playbuilder.daemon();
	    recbuilder.daemon();

	    final Player player = playbuilder.build();
	    final Recorder recorder = recbuilder.build();

	    final ExecutorService executor = Executors.newSingleThreadExecutor();

	    /* データ系列を作成 */
	    final ObservableList<XYChart.Data<Number, Number>> data =
	      IntStream.range(-recorder.getFrameSize(), 0)
	        .mapToDouble(i -> i / recorder.getSampleRate())
	        .mapToObj(t -> new XYChart.Data<Number, Number>(t, 0.0))
	        .collect(Collectors.toCollection(FXCollections::observableArrayList));

	    /* データ系列に名前をつける */
	    final XYChart.Series<Number, Number> series =
	      new XYChart.Series<>("Waveform", data);

	    /* 波形リアルタイム表示 */
	    /* 軸を作成 */
	    /* 時間軸（横軸） */
	    final NumberAxis xAxis = new NumberAxis(
	      /* axisLabel  = */ "Time (seconds)",
	      /* lowerBound = */ -frameDuration,
	      /* upperBound = */ 0.0,
	      /* tickUnit   = */ Le4MusicUtils.autoTickUnit(frameDuration)
	    );
	    /* 周波数軸（縦軸） */
	    final NumberAxis yAxis = new NumberAxis(
	      /* axisLabel  = */ "Amplitude",
	      /* lowerBound = */ -1.0,
	      /* upperBound = */ +1.0,
	      /* tickUnit   = */ Le4MusicUtils.autoTickUnit(1.0 * 2)
	    );

	    /* チャートを作成 */
	    final LineChart<Number, Number> chart = new LineChart<>(xAxis, yAxis);
	    chart.setTitle("Waveform");
	    chart.setLegendVisible(false);
	    /* データの追加・削除時にアニメーション（フェードイン・アウトなど）しない */
	    chart.setAnimated(false);
	    /* データアイテムに対してシンボルを作成しない */
	    chart.setCreateSymbols(false);

	    chart.getData().add(series);

	    /* 描画ウインドウ作成 */
	    final Scene scene  = new Scene(chart, 800, 600);
	    scene.getStylesheets().add("src/le4music.css");
	    primaryStage.setScene(scene);
	    primaryStage.setTitle(getClass().getName());
	    /* ウインドウを閉じたときに他スレッドも停止させる */
	    primaryStage.setOnCloseRequest(req -> executor.shutdown());
	    primaryStage.show();

	    recorder.addAudioFrameListener((frame, position) -> executor.execute(() -> {
	      IntStream.range(0, recorder.getFrameSize()).forEach(i -> {
	        final XYChart.Data<Number, Number> datum = data.get(i);
	        datum.setXValue((i + position - recorder.getFrameSize()) / recorder.getSampleRate());
	        datum.setYValue(frame[i]);
	      });
	      final double posInSec = position / recorder.getSampleRate();
	      xAxis.setLowerBound(posInSec - frameDuration);
	      xAxis.setUpperBound(posInSec);
	    }));

	    /* 録音開始 */
	    Platform.runLater(recorder::start);



	}
}
