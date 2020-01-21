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
	    // 10.0
	    final double interval =
	    	      Optional.ofNullable(cmd.getOptionValue("interval"))
	    	        .map(Double::parseDouble)
	    	        .orElse(Le4MusicUtils.frameInterval);
	    // 0.02
	    final double frameDuration =
	    	      Optional.ofNullable(cmd.getOptionValue("frame"))
	    	        .map(Double::parseDouble)
	    	        .orElse(Le4MusicUtils.frameDuration);
	    // 0.2
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

	    /* 定数の定義 */
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
	    /* 33番目から84番目までのノードの周波数の列 */
	    double[] frequencies = new double[49];
	    frequencies[0] = 65.4;
	    frequencies[1] = 69.3;
	    frequencies[2] = 73.4;
	    frequencies[3] = 77.8;
	    frequencies[4] = 82.4;
	    frequencies[5] = 87.3;
	    frequencies[6] = 92.5;
	    frequencies[7] = 98.0;
	    frequencies[8] = 103.8;
	    frequencies[9] = 110.0;
	    frequencies[10] = 116.5;
	    frequencies[11] = 123.4;
	    frequencies[48] = 1046.5;
	    for(int i=1;i<4;i++) {
	    	for(int j=0;j<12;j++) {
	    		frequencies[12*i+j] = frequencies[12*(i-1)+j]*2;
	    	}
	    }
	    /* 33番目から117番目までのノードの周波数の列 */
	    int nodenums[] = new int[49];
	    int p = 0, q = 0;
	    while(q < 49 && p < 400) {
	    	if(freqs[p] > frequencies[q]) {
	    		nodenums[q] = p;
	    		q++;
	    		p++;
	    	}
	    	p++;
	    }
	    String[] PitchSamples = {
	    		"C2","C#2","D2","D#2","E2","F2","F#2","G2","G#2","A2","A#2","B2",
	    		"C3","C#3","D3","D#3","E3","F3","F#3","G3","G#3","A3","A#3","B3",
	    		"C4","C#4","D4","D#4","E4","F4","F#4","G4","G#4","A4","A#4","B4",
	    		"C5","C#5","D5","D#5","E5","F5","F#5","G5","G#5","A5","A#5","B5",
	    		"C6","No Signal"};

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
	    primaryStage.setScene(scene);
	    primaryStage.setTitle(getClass().getName());
	    /* ウインドウを閉じたときに他スレッドも停止させる */
	    primaryStage.setOnCloseRequest(req -> executor.shutdown());
	    primaryStage.show();

	    /* 録音したデータを解析する */
	    recorder.addAudioFrameListener((frame, position) -> executor.execute(() -> {
	      IntStream.range(0, recorder.getFrameSize()).forEach(i -> {
	        final XYChart.Data<Number, Number> datum = data.get(i);
	        datum.setXValue((i + position - recorder.getFrameSize()) / recorder.getSampleRate());
	        datum.setYValue(frame[i]);

	        final double[] wframe = MathArrays.ebeMultiply(frame, window);
	        final Complex[] spectrum = Le4MusicUtils.rfft(Arrays.copyOf(wframe, fftSize));
	        final double [] spec = Arrays.stream(spectrum)
	        		.mapToDouble(c->Math.log10(c.abs())).toArray();
	        final double[] cepstrum = Arrays.stream(Le4MusicUtils.fft(Arrays.copyOfRange(spec,0,spec.length-1)))
	        		.mapToDouble(c->c.getReal()).toArray();
	        double fixFreq = 50;
	        int max_time = 0;
	        for (int j = 10; j < 200; j++) {
	        	if(fixFreq < cepstrum[j]) {
	        			fixFreq = cepstrum[j];
	        			max_time = j;
	        	}}
	        if (max_time > 50 && 1000 > max_time) {
        		double fundFreq = 1 / (max_time * frameDuration / (cepstrum.length - 1));
        	}else {
        		double fundFreq = 0;
        	}
	        double[] candidates = new double[25];
	        for(int j=0;j<=24;j++) {
	        	double f = frequencies[j];
	        	double each_value = 0;
	        	int r=1;
	        	while(r<=3) {
	        		double fp = f * r;
	        		int fix_num = 0;
	        		for(int k=0;k<49;k++) {
	        			if(frequencies[k] >= fp) {
	        				fix_num = k;
	        				break;
	        			}
	        		}
	        		each_value += cepstrum[nodenums[fix_num]] / r;
	        		r++;
	        	}
	        	candidates[j] = each_value;
	        }
	        int nodenum = 0;
	        double fix_val = 0;
	        for(int j=0;j<25;j++) {
	        	if (fix_val <= candidates[j]) {
	        		nodenum = j;
	        		fix_val = candidates[j];
	        	}
	        }
	        String pitch = PitchSamples[nodenum];
	      });

	      /* 軸の更新 */
	      final double posInSec = position / player.getSampleRate();
	      // final double posInSec = position / recorder.getSampleRate();
	      // play duration = 10.0
	      xAxis.setLowerBound(posInSec - duration);
	      // rec frameDuration = 0.2
	      // xAxis.setLowerBound(posInSec - frameDuration);
	      xAxis.setUpperBound(posInSec);
	    }));

	    /* 録音開始 */
	    Platform.runLater(recorder::start);
	}
}
