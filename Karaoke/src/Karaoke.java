import java.lang.invoke.MethodHandles;
import java.io.File;
import java.util.Arrays;
import java.util.Optional;
import java.util.stream.IntStream;
import java.util.stream.Collectors;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Mixer;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.stage.Stage;
import javafx.scene.Scene;
import javafx.scene.chart.XYChart;
import javafx.scene.control.Label;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.VBox;
import javafx.scene.text.Font;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.NumberAxis;
import javafx.collections.ObservableList;
import javafx.geometry.Pos;
import javafx.collections.FXCollections;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.HelpFormatter;

import jp.ac.kyoto_u.kuis.le4music.Le4MusicUtils;
import jp.ac.kyoto_u.kuis.le4music.LineChartWithSpectrogram;
import jp.ac.kyoto_u.kuis.le4music.Player;
import jp.ac.kyoto_u.kuis.le4music.Recorder;
import static jp.ac.kyoto_u.kuis.le4music.Le4MusicUtils.verbose;

import java.io.IOException;
import javax.sound.sampled.UnsupportedAudioFileException;
import javax.sound.sampled.LineUnavailableException;
import org.apache.commons.cli.ParseException;
import org.apache.commons.math3.complex.Complex;
import org.apache.commons.math3.util.MathArrays;

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

  @Override /* Application */
  public final void start(final Stage primaryStage)
  throws IOException,
         UnsupportedAudioFileException,
         LineUnavailableException,
         ParseException {
    /* コマンドライン引数処理 */
    final String[] args = getParameters().getRaw().toArray(new String[0]);
    final CommandLine cmd = new DefaultParser().parse(options, args);
    if (cmd.hasOption("help")) {
      new HelpFormatter().printHelp(helpMessage, options);
      Platform.exit();
      return;
    }
    verbose = cmd.hasOption("verbose");
    
    /* ファイルの受けとり */
    final String[] pargs = cmd.getArgs();
    if (pargs.length < 1) {
      System.out.println("WAVFILE is not given.");
      new HelpFormatter().printHelp(helpMessage, options);
      Platform.exit();
      return;
    }
    final File wavFile = new File(pargs[0]);

    /* 定数定義 */
    final double frameDuration =
    		Optional.ofNullable(cmd.getOptionValue("frame"))
    		.map(Double::parseDouble)
    		.orElse(Le4MusicUtils.frameDuration);
    final double duration =
    		Optional.ofNullable(cmd.getOptionValue("duration"))
    	    .map(Double::parseDouble)
    	    .orElse(Le4MusicUtils.spectrogramDuration);
    final double interval =
    	    Optional.ofNullable(cmd.getOptionValue("interval"))
    	    .map(Double::parseDouble)
	        .orElse(Le4MusicUtils.frameInterval);

    /* Recorderオブジェクトを生成 */
    final Recorder.Builder recbuilder = Recorder.builder();
    Optional.ofNullable(cmd.getOptionValue("rate"))
      .map(Float::parseFloat)
      .ifPresent(recbuilder::sampleRate);
    Optional.ofNullable(cmd.getOptionValue("mixer"))
      .map(Integer::parseInt)
      .map(index -> AudioSystem.getMixerInfo()[index])
      .ifPresent(recbuilder::mixer);
    Optional.ofNullable(cmd.getOptionValue("outfile"))
      .map(File::new)
      .ifPresent(recbuilder::wavFile);
    recbuilder.frameDuration(frameDuration);
    Optional.ofNullable(cmd.getOptionValue("interval"))
      .map(Double::parseDouble)
      .ifPresent(recbuilder::interval);
    recbuilder.daemon();
    final Recorder recorder = recbuilder.build();

    /* Player を作成 */
    final Player.Builder playbuilder = Player.builder(wavFile);
    Optional.ofNullable(cmd.getOptionValue("mixer"))
      .map(Integer::parseInt)
      .map(index -> AudioSystem.getMixerInfo()[index])
      .ifPresent(playbuilder::mixer);
    if (cmd.hasOption("loop"))
      playbuilder.loop();
    Optional.ofNullable(cmd.getOptionValue("buffer"))
      .map(Double::parseDouble)
      .ifPresent(playbuilder::bufferDuration);
    Optional.ofNullable(cmd.getOptionValue("frame"))
      .map(Double::parseDouble)
      .ifPresent(playbuilder::frameDuration);
    playbuilder.interval(interval);
    playbuilder.daemon();
    final Player player = playbuilder.build();
    
    /* データ処理スレッド */
    final ExecutorService executor = Executors.newSingleThreadExecutor();

    /* データ系列を作成 */
    final ObservableList<XYChart.Data<Number, Number>> data1 =
      IntStream.range(-recorder.getFrameSize(), 0)
        .mapToDouble(i -> i / recorder.getSampleRate())
        .mapToObj(t -> new XYChart.Data<Number, Number>(t, 0.0))
        .collect(Collectors.toCollection(FXCollections::observableArrayList));

    /* データ系列に名前をつける */
    final XYChart.Series<Number, Number> series1 =
      new XYChart.Series<>("Waveform", data1);

    /* 波形リアルタイム表示 */
    /* 軸を作成 */
    /* 時間軸（横軸） */
    final NumberAxis xAxis1 = new NumberAxis(
      /* axisLabel  = */ "Time (seconds)",
      /* lowerBound = */ -duration,
      /* upperBound = */ 0.0,
      /* tickUnit   = */ Le4MusicUtils.autoTickUnit(duration)
    );
    /* 周波数軸（縦軸） */
    final NumberAxis yAxis1 = new NumberAxis(
      /* axisLabel  = */ "Amplitude",
      /* lowerBound = */ 0.0,
      /* upperBound = */ +1000.0,
      /* tickUnit   = */ Le4MusicUtils.autoTickUnit(1.0 * 2)
    );

    /* 窓関数とFFTのサンプル数 */
    final int fftSize = 1 << Le4MusicUtils.nextPow2(player.getFrameSize());
    final int fftSize2 = (fftSize >> 1) + 1;

    /* 窓関数を求め，それを正規化する */
    final double[] window =
      MathArrays.normalizeArray(Le4MusicUtils.hanning(player.getFrameSize()), 1.0);

    /* 各フーリエ変換係数に対応する周波数 */
    final double[] freqs =
      IntStream.range(0, fftSize2)
               .mapToDouble(i -> i * player.getSampleRate() / fftSize)
               .toArray();

    /* フレーム数 */
    final int frames = (int)Math.round(duration / interval);

    
    /* チャートを作成 */
    final LineChartWithMarker<Number, Number> chart1 = new LineChartWithMarker<>(xAxis1, yAxis1);
    chart1.setTitle("Waveform");
    chart1.setLegendVisible(false);
    /* データの追加・削除時にアニメーション（フェードイン・アウトなど）しない */
    chart1.setAnimated(false);
    /* データアイテムに対してシンボルを作成しない */
    chart1.setCreateSymbols(false);

    chart1.getData().add(series1);

    /* 描画ウインドウ作成 */
    final Scene scene1  = new Scene(chart1, 800, 600);

    recorder.addAudioFrameListener((frame, position) -> executor.execute(() -> {
    	final double[] wframe = MathArrays.ebeMultiply(frame, window);
        final Complex[] spectrum = Le4MusicUtils.rfft(Arrays.copyOf(wframe, fftSize));
        final double [] spec = Arrays.stream(spectrum)
        		.mapToDouble(c->Math.log10(c.abs())).toArray();
        final double [] spec_volume = Arrays.stream(spectrum)
        		.mapToDouble(c->c.abs()).toArray();
        double app = 0.0;
        for(int j=0;j<spec_volume.length;j++) {
    		app += Math.pow(spec_volume[j],2);
    	}
        app = 20 * Math.log10(Math.sqrt(app / spec_volume.length)/ (2 * 0.00001));
        if (app<0){
    		app = 0;
    	}
        final double[] cepstrum = Arrays.stream(Le4MusicUtils.fft(Arrays.copyOfRange(spec,0,spec.length-1)))
        		.mapToDouble(c->c.getReal()).toArray();
        double fixFreq = 50;
        int max_time = 0;
        for (int j = 10; j < 200; j++) {
        	if(fixFreq < cepstrum[j]) {
        			fixFreq = cepstrum[j];
        			max_time = j;
        	}}
        double fundFreq;
        if (max_time > 50 && 1000 > max_time) {
    		fundFreq = 1 / (max_time * frameDuration / (cepstrum.length - 1));
    	}else {
    		fundFreq = 0;
    	}
        IntStream.range(0, recorder.getFrameSize()).forEach(i -> {
        	final XYChart.Data<Number, Number> datum = data1.get(i);
        	datum.setXValue((i + position - recorder.getFrameSize()) / recorder.getSampleRate());
        	datum.setYValue(fundFreq);
        });
        final double posInSec = position / recorder.getSampleRate();
        xAxis1.setLowerBound(posInSec - frameDuration);
        xAxis1.setUpperBound(posInSec);
    }));
    
    // player.getSampleRate = recoder.getSampleRate = 16000
    
    /* 軸を作成 */
    final NumberAxis xAxis2 = new NumberAxis(
      /* axisLabel  = */ "Time (seconds)",
      /* lowerBound = */ -duration,
      /* upperBound = */ 0,
      /* tickUnit   = */ Le4MusicUtils.autoTickUnit(duration)
    );
    xAxis2.setAnimated(false);

    final double freqLowerBound2 =
      Optional.ofNullable(cmd.getOptionValue("freq-lo"))
        .map(Double::parseDouble)
        .orElse(0.0);
    if (freqLowerBound2 < 0.0)
      throw new IllegalArgumentException(
        "freq-lo must be non-negative: " + freqLowerBound2
      );
    final double freqUpperBound2 =
      Optional.ofNullable(cmd.getOptionValue("freq-up"))
        .map(Double::parseDouble)
        .orElse(player.getNyquist());
    if (freqUpperBound2 <= freqLowerBound2)
      throw new IllegalArgumentException(
        "freq-up must be larger than freq-lo: " +
        "freq-lo = " + freqLowerBound2 + ", freq-up = " + freqUpperBound2
      );
    final NumberAxis yAxis2 = new NumberAxis(
      /* axisLabel  = */ "Frequency (Hz)",
      /* lowerBound = */ freqLowerBound2,
      /* upperBound = */ freqUpperBound2,
      /* tickUnit   = */ Le4MusicUtils.autoTickUnit(freqUpperBound2 - freqLowerBound2)
    );
    yAxis2.setAnimated(false);

    /* スペクトログラム表示chart */
    final LineChartWithSpectrogram<Number, Number> chart2 =
      new LineChartWithSpectrogram<>(xAxis2, yAxis2);
    chart2.setParameters(frames, fftSize2, player.getNyquist());
    chart2.setTitle("Spectrogram");

    /* グラフ描画 */
    final Scene scene2 = new Scene(chart2, 800, 600);

    player.addAudioFrameListener((frame, position) -> executor.execute(() -> {
      final double[] wframe = MathArrays.ebeMultiply(frame, window);
      final Complex[] spectrum = Le4MusicUtils.rfft(Arrays.copyOf(wframe, fftSize));
      final double posInSec = position / player.getSampleRate();

      /* スペクトログラム描画 */
      chart2.addSpectrum(spectrum);

      /* 軸を更新 */
      xAxis2.setUpperBound(posInSec);
      xAxis2.setLowerBound(posInSec - duration);
    }));

    /* 録音開始 */
    Platform.runLater(player::start);

    BorderPane borderPane = new BorderPane();
    borderPane.setCenter(chart1);
    borderPane.setTop(chart2);
    primaryStage.setTitle(getClass().getName());
    /* ウインドウを閉じたときに他スレッドも停止させる */
    primaryStage.setScene(new Scene(borderPane));
    primaryStage.setOnCloseRequest(req -> executor.shutdown());
    primaryStage.show();
    Platform.setImplicitExit(true);
    
    /* 録音開始 */
    Platform.runLater(recorder::start);
  }

}
