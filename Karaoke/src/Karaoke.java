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
import javafx.scene.chart.XYChart.Data;
import javafx.scene.control.Label;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.VBox;
import javafx.scene.text.Font;
import javafx.scene.text.Text;
import javafx.scene.text.TextAlignment;
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
      /* lowerBound = */ -frameDuration,
      /* upperBound = */ 0.0,
      /* tickUnit   = */ Le4MusicUtils.autoTickUnit(duration)
    );
    /* 周波数軸（縦軸） */
    final NumberAxis yAxis1 = new NumberAxis(
      /* axisLabel  = */ "Node Number",
      /* lowerBound = */ 36.0,
      /* upperBound = */ +60.0,
      /* tickUnit   = */ 2
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

    
    /* フレーム数 */
    final int frames = (int)Math.round(duration / interval);
    final int size = (int)(duration / interval);
    
    /* チャートを作成 */
    final LineChartWithMarker<Number, Number> chart1 = new LineChartWithMarker<>(xAxis1, yAxis1);
    chart1.setTitle("Pitch");
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
        final double volume = 20 * Math.log10(Math.sqrt(app / spec_volume.length)/ (2 * 0.00001));
        
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
        
        /* SSHを使って目的の基本周波数を推定する */
        /* 候補を36から60にする */
        double[] candidates = new double[25];
        for(int i=0;i<=24;i++) {
        	double f = frequencies[i];
        	double each_value = 0;
        	int r=1;
        	while(r<=3) {
        		double fp = f * r;
        		int first_num = 0;
        		for(int j=0;j<49;j++) {
        			if(frequencies[j] >= fp) {
        				first_num = j;
        				break;
        			}
        		}
        		each_value += spec_volume[nodenums[first_num]] / r;
        		r++;
        	}
        	candidates[i] = each_value;
        }
        int ans = 0;
        double ans_val = 0;
        for(int i=0;i<25;i++) {
        	if (ans_val <= candidates[i]) {
        		ans = i;
        		ans_val = candidates[i];
        	}
        }
        ans += 36;
        if(volume < 10) {
        	ans = 0;
        }
        final int basicfqs = ans;
        
        final double posInSec = position / recorder.getSampleRate();
        
        IntStream.range(0,size).forEach(i -> {
        	final XYChart.Data<Number, Number> datum = data1.get(i);
        	if(i == size - 1) {
        		datum.setXValue(posInSec);
        		datum.setYValue(basicfqs);
        	}else {
        		final XYChart.Data<Number, Number> datum1 = data1.get(i+1);
        		datum.setXValue(datum1.getXValue());
        		datum.setYValue(datum1.getYValue());
        	}
        });
        xAxis1.setLowerBound(posInSec - duration);
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

    String[] Lyrics = {
    		"あたしあなたに会えて本当に嬉しいのに" // 0
    		,"当たり前のようにそれらすべてが悲しいんだ" // 1
    		,"今痛いくらい幸せな思い出が" // 2
    		,"いつか来るお別れを育てて歩く" // 3
    		,"誰かの居場所を奪い生きるくらいならばもう"
    		,"あたしは石ころにでもなれたならいいな"
    		,"だとしたら勘違いも戸惑いもない"
    		,"そうやってあなたまでも知らないままで"
    		,"あなたにあたしの思いが全部伝わってほしいのに"
    		,"誰にも言えない秘密があって嘘をついてしまうのだ"
    		,"あなたが思えば思うよりいくつもあたしは意気地ないのに"
    		,"どうして"
    		,"消えない悲しみも綻びもあなたといれば"
    		,"それでよかったねと笑えるのがどんなに嬉しいか"
    		,"目の前の全てがぼやけては溶けてゆくような"
    		,"奇跡であふれて足りないや"
    		,"あたしの名前を呼んでくれた"
    		,"あなたが居場所を失くし彷徨うくらいならばもう"
    		,"誰かが身代わりになればなんて思うんだ"
    		, "今細やかで確かな見ないふり"
    		, "きっと繰り返しながら笑い合うんだ"
    		,"何度誓っても何度祈っても惨憺たる夢を見る"
    		,"小さな歪みがいつかあなたを呑んでなくしてしまうような"
    		,"あなたが思えば思うより大げさにあたしは不甲斐ないのに"
    		,"どうして"
    		,"お願い　いつまでもいつまでも超えられない夜を"
    		,"超えようと手をつなぐこの日々が続きますように"
    		,"閉じた瞼さえ鮮やかに彩るために"
    		,"そのために何ができるかな"
    		,"あなたの名前を呼んでいいかな"
    		,"生まれてきたその瞬間に私" //30
    		,"消えてしまいたいって泣き喚いたんだ" //31
    		,"それからずっと探していたんだ" //32
    		,"いつか出会えるあなたのことを" //33
    		,"消えない悲しみも綻びもあなたがいれば" //34
    		,"それでよかったねと笑えるのがどんなに嬉しいか" //35
    		,"目の前の全てががぼやけて溶けていくような" //36
    		,"奇跡で溢れて足りないや" //37
    		,"私の名前を呼んでくれた" // 38
       		,"あなたの名前を呼んでいいかな" // 39
    };
    
    Text label1 = new Text("");
    label1.setFont(Font.font("Arial", 20));
    label1.setTextAlignment(TextAlignment.CENTER);
    
    player.addAudioFrameListener((frame, position) -> executor.execute(() -> {
      final double[] wframe = MathArrays.ebeMultiply(frame, window);
      final Complex[] spectrum = Le4MusicUtils.rfft(Arrays.copyOf(wframe, fftSize));
      final double posInSec = position / player.getSampleRate();
      if(posInSec < 0.5 && 0 < posInSec) {
    	  label1.setText(Lyrics[0]);
      }
      else if(posInSec < 6.5 && 6 < posInSec) {
    	  label1.setText(Lyrics[1]);
      }else if(posInSec < 12.5 && 12 < posInSec) {
    	  label1.setText(Lyrics[2]);
      }else if(posInSec < 17.5 && 17 < posInSec) {
    	  label1.setText(Lyrics[3]);
      }else if(posInSec < 25 && 24.5 < posInSec) {
    	  label1.setText(Lyrics[4]);
      }else if(posInSec < 31 && 30.5 < posInSec) {
    	  label1.setText(Lyrics[5]);
      }else if(posInSec < 37 && 36.5 < posInSec) {
    	  label1.setText(Lyrics[6]);
      }else if(posInSec < 42 && 41.5 < posInSec) {
    	  label1.setText(Lyrics[7]);
      }else if(posInSec < 49 && 48.5 < posInSec) {
    	  label1.setText(Lyrics[8]);
      }else if(posInSec < 55 && 54.5 < posInSec) {
    	  label1.setText(Lyrics[9]);
      }else if(posInSec < 61.5 && 60 < posInSec) {
    	  label1.setText(Lyrics[10]);
      }else if(posInSec < 67 && 66.5 < posInSec) {
    	  label1.setText(Lyrics[11]);
      }else if(posInSec < 75 && 74.5 < posInSec) {
    	  label1.setText(Lyrics[12]);
      }else if(posInSec < 82 && 81.5 < posInSec) {
    	  label1.setText(Lyrics[13]);
      }else if(posInSec < 87 && 86.5 < posInSec) {
    	  label1.setText(Lyrics[14]);
      }else if(posInSec < 93 && 92.5 < posInSec) {
    	  label1.setText(Lyrics[15]);
      }else if(posInSec < 98.5 && 98 < posInSec) {
    	  label1.setText(Lyrics[16]);
      }else if(posInSec < 108 && 107.5 < posInSec) {
    	  label1.setText(Lyrics[17]);
      }else if(posInSec < 115 && 114.5 < posInSec) {
    	  label1.setText(Lyrics[18]);
      }else if(posInSec < 121 && 120.5 < posInSec) {
    	  label1.setText(Lyrics[19]);
      }else if(posInSec < 126 && 125.5 < posInSec) {
    	  label1.setText(Lyrics[20]);
      }else if(posInSec < 132 && 131.5 < posInSec) {
    	  label1.setText(Lyrics[21]);
      }else if(posInSec < 140 && 139.5 < posInSec) {
    	  label1.setText(Lyrics[22]);
      }else if(posInSec < 146 && 145.5 < posInSec) {
    	  label1.setText(Lyrics[23]);
      }else if(posInSec < 152 && 151.5 < posInSec) {
    	  label1.setText(Lyrics[24]);
      }else if(posInSec < 158 && 157.5 < posInSec) {
    	  label1.setText(Lyrics[25]);
      }else if(posInSec < 165 && 164.5 < posInSec) {
    	  label1.setText(Lyrics[26]);
      }else if(posInSec < 171 && 170.5 < posInSec) {
    	  label1.setText(Lyrics[27]);
      }else if(posInSec < 176 && 175.5 < posInSec) {
    	  label1.setText(Lyrics[28]);
      }else if(posInSec < 182.5 && 182 < posInSec) {
    	  label1.setText(Lyrics[29]);
      }else if(posInSec < 214 && 213.5 < posInSec) {
    	  label1.setText(Lyrics[30]);
      }else if(posInSec < 220 && 219.5 < posInSec) {
    	  label1.setText(Lyrics[31]);
      }else if(posInSec < 225 && 224.5 < posInSec) {
    	  label1.setText(Lyrics[32]);
      }else if(posInSec < 230 && 229.5 < posInSec) {
    	  label1.setText(Lyrics[33]);
      }else if(posInSec < 242 && 241.5 < posInSec) {
    	  label1.setText(Lyrics[34]);
      }else if(posInSec < 250 && 249.5 < posInSec) {
    	  label1.setText(Lyrics[35]);
      }else if(posInSec < 262 && 261.5 < posInSec) {
    	  label1.setText(Lyrics[36]);
      }else if(posInSec < 268 && 267.5 < posInSec) {
    	  label1.setText(Lyrics[37]);
      }else if(posInSec < 274 && 274.5 < posInSec) {
    	  label1.setText(Lyrics[38]);
      }else if(posInSec < 281 && 280.5 < posInSec) {
    	  label1.setText(Lyrics[39]);
      }
      else if(posInSec < 291 && 290.5 < posInSec) {
    	  label1.setText("");
      }
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
    borderPane.setBottom(label1);
    BorderPane.setAlignment(label1, Pos.CENTER);
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
