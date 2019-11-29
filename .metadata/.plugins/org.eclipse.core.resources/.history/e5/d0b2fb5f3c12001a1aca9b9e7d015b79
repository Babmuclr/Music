import java.io.File;
import java.util.Arrays;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.stage.Stage;
import javafx.scene.Scene;
import javafx.scene.chart.XYChart;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.NumberAxis;
import javafx.collections.ObservableList;
import javafx.collections.FXCollections;

import jp.ac.kyoto_u.kuis.le4music.Le4MusicUtils;
import org.apache.commons.math3.complex.Complex;

import java.io.IOException;
import javax.sound.sampled.UnsupportedAudioFileException;

public final class PlotSpectrumSimple extends Application {

@Override public final void start(final Stage primaryStage)
throws IOException,
UnsupportedAudioFileException {
/* コ マ ン ド ラ イ ン 引 数 処 理 */

final String[] args = getParameters().getRaw().toArray(new String[0]);
if (args.length < 1) {
System.out.println("WAVFILE is not given.");
Platform.exit();
return;
}
final File wavFile = new File(args[0]);

/* W A V フ ァ イ ル 読 み 込 み */
final AudioInputStream stream = AudioSystem.getAudioInputStream(wavFile);
final double[] waveform = Le4MusicUtils.readWaveformMonaural(stream);
final AudioFormat format = stream.getFormat();
final double sampleRate = format.getSampleRate();
stream.close();

/* fftSize = 2ˆp >= waveform.length を 満 た す fftSize を 求 め る
* 2ˆp は シ フ ト 演 算 で 求 め る */
final int fftSize = 1 << Le4MusicUtils.nextPow2(waveform.length);
final int fftSize2 = (fftSize >> 1) + 1;
/* 信 号 の 長 さ を fftSize に 伸 ば し ， 長 さ が 足 り な い 部 分 は0 で 埋 め る ．
* 振 幅 を 信 号 長 で 正 規 化 す る ． */
final double[] src =
Arrays.stream(Arrays.copyOf(waveform, fftSize))
.map(w -> w / waveform.length)
.toArray();
/* 高 速 フ ー リ エ 変 換 を 行 う */
final Complex[] spectrum = Le4MusicUtils.rfft(src);

/* 対 数 振 幅 ス ペ ク ト ル を 求 め る */
final double[] specLog =
Arrays.stream(spectrum)
.mapToDouble(c -> 20.0 * Math.log10(c.abs()))
.toArray();

/* デ ー タ 系 列 を 作 成 */
final ObservableList<XYChart.Data<Number, Number>> data =
IntStream.range(0, fftSize2)
.mapToObj(i -> new XYChart.Data<Number, Number>(i * sampleRate / fftSize, specLog[i]))
.collect(Collectors.toCollection(FXCollections::observableArrayList));

/* デ ー タ 系 列 に 名 前 を つ け る */
final XYChart.Series<Number, Number> series = new XYChart.Series<>("Spectrum", data);

/* 軸 を 作 成 */
final NumberAxis xAxis = new NumberAxis();
xAxis.setLabel("Frequency (Hz)");
final NumberAxis yAxis = new NumberAxis();
yAxis.setLabel("Amplitude (dB)");

/* チ ャ ー ト を 作 成 */
final LineChart<Number, Number> chart = new LineChart<>(xAxis, yAxis);
chart.setTitle("Spectrum");
chart.setCreateSymbols(false);
chart.getData().add(series);

/* グ ラ フ 描 画 */
final Scene scene = new Scene(chart, 800, 600);


/* ウ イ ン ド ウ 表 示 */
primaryStage.setScene(scene);
primaryStage.setTitle(getClass().getName());
primaryStage.show();
}

}
