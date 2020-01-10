import java.lang.invoke.MethodHandles;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Observable;
import java.util.Optional;
import java.util.stream.Stream;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import javax.sound.sampled.AudioSystem;
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

import java.io.IOException;
import javax.sound.sampled.UnsupportedAudioFileException;
import org.apache.commons.cli.ParseException;

public final class SoundGUI extends Application {

private static final Options options = new Options();
private static final String helpMessage =
MethodHandles.lookup().lookupClass().getName() + " [OPTIONS] <WAVFILE>";

static {
/* コ マ ン ド ラ イ ン オ プ シ ョ ン 定 義 */
options.addOption("h", "help", false, "Display this help and exit");
options.addOption("o", "outfile", true,
"Output image file (Default: " +
MethodHandles.lookup().lookupClass().getSimpleName() +
"." + Le4MusicUtils.outputImageExt + ")");
options.addOption("f", "frame", true,
"Duration of frame [seconds] (Default: " +
Le4MusicUtils.frameDuration + ")");
options.addOption("s", "shift", true,
"Duration of shift [seconds] (Default: frame/8)");
}

@Override public final void start(final Stage primaryStage)
throws IOException,
UnsupportedAudioFileException,
ParseException {
/* コ マ ン ド ラ イ ン 引 数 処 理 */
final String[] args = getParameters().getRaw().toArray(new String[0]);
final CommandLine cmd = new DefaultParser().parse(options, args);
if (cmd.hasOption("help")) {

new HelpFormatter().printHelp(helpMessage, options);
Platform.exit();
return;
}
final String[] pargs = cmd.getArgs();
if (pargs.length < 1) {
System.out.println("WAVFILE is not given.");
new HelpFormatter().printHelp(helpMessage, options);
Platform.exit();
return;
}
/* ファイルを入力する */
final File awavFile = new File(pargs[0]);
final File iwavFile = new File(pargs[1]);
final File uwavFile = new File(pargs[2]);
final File ewavFile = new File(pargs[3]);
final File owavFile = new File(pargs[4]);
final File wavFile = new File(pargs[5]);

/* W A V フ ァ イ ル 読 み 込 み */
final AudioInputStream stream = AudioSystem.getAudioInputStream(wavFile);
final double[] waveform = Le4MusicUtils.readWaveformMonaural(stream);
final AudioFormat format = stream.getFormat();
final double sampleRate = format.getSampleRate();
final double nyquist = sampleRate * 0.5;

double[] air = new double[3200];
double[] zerowaveform = new double[waveform.length + air.length];
System.arraycopy(waveform, 0, zerowaveform, 0, waveform.length);
System.arraycopy(air, 0, zerowaveform, waveform.length, air.length);

stream.close();

/* 窓 関 数 と F F T の サ ン プ ル 数 */
final double frameDuration =
Optional.ofNullable(cmd.getOptionValue("frame"))
.map(Double::parseDouble)
.orElse(Le4MusicUtils.frameDuration);
final int frameSize = (int)Math.round(frameDuration * sampleRate);
final int fftSize = 1 << Le4MusicUtils.nextPow2(frameSize);
final int fftSize2 = (fftSize >> 1) + 1;

/* シ フ ト の サ ン プ ル 数 */
final double shiftDuration =
Optional.ofNullable(cmd.getOptionValue("shift"))
.map(Double::parseDouble)
.orElse(Le4MusicUtils.frameDuration / 8);
final int shiftSize = (int)Math.round(shiftDuration * sampleRate);

/* 窓 関 数 を 求 め ， そ れ を 正 規 化 す る */
final double[] window = MathArrays.normalizeArray(
Arrays.copyOf(Le4MusicUtils.hanning(frameSize), fftSize), 1.0
);

/* 短 時 間 フ ー リ エ 変 換 本 体 */
final Stream<Complex[]> spectrogram1 =
Le4MusicUtils.sliding(waveform, window, shiftSize)
.map(frame -> Le4MusicUtils.rfft(frame));

/* 複 素 ス ペ ク ト ロ グ ラ ム を 対 数 振 幅 ス ペ ク ト ロ グ ラ ム に */
final double[][] specLog1 =
spectrogram1.map(sp -> Arrays.stream(sp)
.mapToDouble(c -> 20.0 * Math.log10(c.abs()))
.toArray())
.toArray(n -> new double[n][]);

/* 参 考 ： フ レ ー ム 数 と 各 フ レ ー ム 先 頭 位 置 の 時 刻 */
final double[] times =
IntStream.range(0, specLog1.length)
.mapToDouble(i -> i * shiftDuration)
.toArray();

/* 参 考 ： 各 フ ー リ エ 変 換 係 数 に 対 応 す る 周 波 数 */
final double[] freqs =
IntStream.range(0, fftSize2)
.mapToDouble(i -> i * sampleRate / fftSize)
.toArray();

final Stream<Complex[]> spectrogram2 =
Le4MusicUtils.sliding(waveform, window, shiftSize)
.map(frame -> Le4MusicUtils.rfft(frame));

final Stream<Complex[]> spectrogram4 =
Le4MusicUtils.sliding(waveform, window, shiftSize)
.map(frame -> Le4MusicUtils.rfft(frame));

/* 複 素 ス ペ ク ト ロ グ ラ ム を 対 数 振 幅 ス ペ ク ト ロ グ ラ ム に */
final double[][] specLog2 =
spectrogram2.map(sp -> Arrays.stream(sp)
.mapToDouble(c -> Math.log10(c.abs()))
.toArray())
.toArray(n -> new double[n][]);

/* 各スペクトラムをケプトラムに変換 */
double[][] cepstrums = new double[specLog2.length][];
for(int i = 0; i < specLog2.length; i++) {
	double[] s = Arrays.copyOfRange(specLog2[i],0,specLog2[i].length-1);
	Complex[] cepstrum = Le4MusicUtils.fft(s);
	cepstrums[i] = Arrays.stream(cepstrum)
			.mapToDouble(c -> c.getReal())
			.toArray();
}

/* 音量を求める */
final double[][] appLog =
spectrogram4.map(sp -> Arrays.stream(sp)
.mapToDouble(c -> c.abs())
.toArray())
.toArray(n -> new double[n][]);

double[] app = new double[appLog.length];

for(int i=0;i<appLog.length;i++) {
	for(int j=0;j<appLog.length;j++) {
		app[i] += Math.pow(appLog[i][j],2);
	}
	app[i] /= appLog.length;
	app[i] = Math.sqrt(app[i]);
	app[i] /= 2 * 0.00001;
	if (20 * Math.log10(app[i])<0){
		app[i] = 0;
	}
	else {
		app[i] = 20 * Math.log10(app[i]);
	}
}

int dicter = 13;

/* 各音叉の学習を行う */
double[][] studyData = new double[10][cepstrums[0].length];
for(int i=0;i<5;i++) {
	AudioInputStream studystream;
	if(i==0) {
		studystream = AudioSystem.getAudioInputStream(awavFile);
	}
	else if(i==1){
		studystream = AudioSystem.getAudioInputStream(iwavFile);
	}
	else if(i==2){
		studystream = AudioSystem.getAudioInputStream(uwavFile);
	}
	else if(i==3){
		studystream = AudioSystem.getAudioInputStream(ewavFile);
	}
	else{
		studystream = AudioSystem.getAudioInputStream(owavFile);
	}
	double[] studywaveform = Le4MusicUtils.readWaveformMonaural(studystream);
	Stream<Complex[]> spectrogram3 = Le4MusicUtils.sliding(studywaveform, window, shiftSize).map(frame -> Le4MusicUtils.rfft(frame));
	double[][] specLog3 = spectrogram3.map(sp -> Arrays.stream(sp).mapToDouble(c -> Math.log10(c.abs())).toArray()).toArray(n -> new double[n][]);
	double[][] studycepstrums = new double[specLog3.length][];
	
	/* ケプストラムを作成 */
	for(int j = 0; j < specLog3.length; j++) {
		double[] s = Arrays.copyOfRange(specLog3[j],0,specLog3[j].length-1);
		Complex[] cepstrum = Le4MusicUtils.fft(s);
		studycepstrums[j] = Arrays.stream(cepstrum).mapToDouble(c -> c.getReal()).toArray();
	}
	/* μを求める */
	for(int j=0;j<studycepstrums.length;j++){
		double ans = 0;
		for(int k=0;k<dicter;k++){
			ans += studycepstrums[k][j];
		}
		studyData[i*2][j] = ans / studycepstrums.length;
	}
	/* σを求める */
	for(int j=0;j<studycepstrums.length;j++){
		double ans = 0;
		for(int k=0;k<dicter;k++){
			ans += Math.pow((studycepstrums[k][j]-studyData[i*2][j]),2);
		}
		studyData[i*2+1][j] = ans / studycepstrums.length;
	}
}

/* ゼロ交差数を求める */
final double durationSpec = frameDuration / (cepstrums[0].length - 1);

int[] zerocross = new int[shiftSize];
for(int i = 0; i < shiftSize; i++) {
	int count = 0;
	for(int j = 0; j < frameSize; j++) {
		int front = i * shiftSize + j;
		int back = i * shiftSize + j + 1;
		if(zerowaveform[front] * zerowaveform[back] <= 0 && Math.abs(zerowaveform[front] - zerowaveform[back]) > 0.001){
			count += 1;
		}
	}
	if(count > 30) {
		zerocross[i] = count;
	}
}

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

int[] basicfqs = new int[specLog1.length];

for(int k = 0;k< specLog1.length;k++) {
	
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
		each_value += appLog[k][nodenums[first_num]] / r;
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
if(zerocross[k]==0) {
	basicfqs[k] = 49;
}
else {
	basicfqs[k] = ans;
}
}

/* basicfqsは音声のノード番号が格納されている
 * ノード番号とC2などの音階とを対応させて出力 */
String[] Pitches = new String[cepstrums.length];
String[] PitchSamples = {
		"C2","C#2","D2","D#2","E2","F2","F#2","G2","G#2","A2","A#2","B2",
		"C3","C#3","D3","D#3","E3","F3","F#3","G3","G#3","A3","A#3","B3",
		"C4","C#4","D4","D#4","E4","F4","F#4","G4","G#4","A4","A#4","B4",
		"C5","C#5","D5","D#5","E5","F5","F#5","G5","G#5","A5","A#5","B5",
		"C6","No Signal"};
for(int i = 0;i < basicfqs.length;i++) {
	Pitches[i] = PitchSamples[basicfqs[i]];
}

/* 基本周波数を求める */
double[] fundFreqs = new double[cepstrums.length];

for(int i = 0; i < cepstrums.length; i++) {
	double fundFreq = 50;
	int ans = 0;
	for (int j = 10; j < 200; j++) {

		
		if(fundFreq < cepstrums[i][j]) {
			fundFreq = cepstrums[i][j];
			ans = j;
		}
	}
	if ((ans < 50 || 1000 < ans) || zerocross[i] == 0){
		fundFreqs[i] = 0;
	}
	else {
		fundFreqs[i] = 1 / (ans * durationSpec);
	}
}

/*　学習結果の適応　*/
double[] dict = new double[cepstrums.length];
String[] dicters = new String[cepstrums.length];
for(int i = 0; i < cepstrums.length; i++) {
	double ans_a = 0;
	double ans_i = 0;
	double ans_u = 0;
	double ans_e = 0;
	double ans_o = 0;
	for(int j = 0; j < dicter; j++) {
		ans_a += Math.log10(studyData[1][j]) + Math.pow(cepstrums[i][j]-studyData[0][j],2) / (2 * Math.pow(studyData[1][j],2));
		ans_i += Math.log10(studyData[3][j]) + Math.pow(cepstrums[i][j]-studyData[2][j],2) / (2 * Math.pow(studyData[3][j],2));
		ans_u += Math.log10(studyData[5][j]) + Math.pow(cepstrums[i][j]-studyData[4][j],2) / (2 * Math.pow(studyData[5][j],2));
		ans_e += Math.log10(studyData[7][j]) + Math.pow(cepstrums[i][j]-studyData[6][j],2) / (2 * Math.pow(studyData[7][j],2));
		ans_o += Math.log10(studyData[9][j]) + Math.pow(cepstrums[i][j]-studyData[8][j],2) / (2 * Math.pow(studyData[9][j],2));
	}
	int ans = 0;
	String str = "";
	
	if(zerocross[i] == 0) {
		ans = 0;
		str = "No Signal";
	}
	else if(ans_a>=ans_i && ans_a>=ans_u && ans_a>=ans_e && ans_a>=ans_o) {
		ans = 100;
		str = "あ";
	}
	else if(ans_i>=ans_a && ans_i>=ans_u && ans_i>=ans_e && ans_i>=ans_o) {
		ans = 200;
		str = "い";
	}
	else if(ans_u>=ans_a && ans_u>=ans_i && ans_u>=ans_e && ans_u>=ans_o) {
		ans = 300;
		str = "う";
	}
	else if(ans_e>=ans_a && ans_e>=ans_i && ans_e>=ans_u && ans_e>=ans_o) {
		ans = 400;
		str = "え";
	}
	else if(ans_o>=ans_a && ans_o>=ans_i && ans_o>=ans_u && ans_o>=ans_e) {
		ans = 500;
		str = "お";
	}
	dict[i] = ans;
	dicters[i] = str;
}

final double duration = (specLog1.length - 1) * shiftDuration;

/* スペクトログラムをchart1として扱う */
final ObservableList<XYChart.Data<Number, Number>> data1 =
IntStream.range(0,dict.length)
.mapToObj(i -> new XYChart.Data<Number, Number>(i* shiftDuration,fundFreqs[i]))
.collect(Collectors.toCollection(FXCollections::observableArrayList));

final XYChart.Series<Number, Number> series1 =
new XYChart.Series<>("Fund Frequency", data1);

/* X 軸 を 作 成 */
final NumberAxis xAxis1 = new NumberAxis(
/* axisLabel = */ "Time (seconds)",
/* lowerBound = */ 0.0,
/* upperBound = */ duration,
/* tickUnit = */ Le4MusicUtils.autoTickUnit(nyquist)
);
xAxis1.setAnimated(false);

/* Y 軸 を 作 成 */
final NumberAxis yAxis1 = new NumberAxis(
/* axisLabel = */ "Frequency (Hz)",
/* lowerBound = */ 0.0,
/* upperBound = */ 5000,
/* tickUnit = */ Le4MusicUtils.autoTickUnit(nyquist)
);
yAxis1.setAnimated(false);

/* チ ャ ー ト を 作 成 */
final LineChartWithMarker<Number, Number> chart1 =
new LineChartWithMarker<>(xAxis1, yAxis1);
chart1.addVerticalValueMarker(new XYChart.Data<>(0,1000));

chart1.setParameters(specLog1.length, fftSize2, nyquist);
chart1.setTitle("Spectrogram");
Arrays.stream(specLog1).forEach(chart1::addSpecLog);
chart1.setCreateSymbols(true);
chart1.setLegendVisible(false);


/* 音量と基本周波数を折れ線グラフにして出力
 * スペクトログラムよりも少し小さめに表示する
 * 余った部分でスライダーで表している部分の具体的な値を表示 */

final ObservableList<XYChart.Data<Number, Number>> data2 =
IntStream.range(0, app.length)
.mapToObj(i -> new XYChart.Data<Number, Number>(i * shiftDuration, app[i]))
.collect(Collectors.toCollection(FXCollections::observableArrayList));

/* デ ー タ 系 列 に 名 前 を つ け る */
final XYChart.Series<Number, Number> series2 = new XYChart.Series<>("Volume", data2);

/* X 軸 を 作 成 */
final NumberAxis xAxis2 = new NumberAxis(
/* axisLabel = */ "Time (seconds)",
/* lowerBound = */ 0.0,
/* upperBound = */ duration,
/* tickUnit = */ Le4MusicUtils.autoTickUnit(nyquist)
);
xAxis2.setAnimated(false);

/* Y 軸 を 作 成 */
final NumberAxis yAxis2 = new NumberAxis(
/* axisLabel = */ "Volume (db) & Frequency (Hz)",
/* lowerBound = */ 0,
/* upperBound = */ 250,
/* tickUnit = */ Le4MusicUtils.autoTickUnit(nyquist)
);
yAxis2.setAnimated(false);

/* チ ャ ー ト を 作 成 */
final LineChartWithMarker<Number, Number> chart2 = new LineChartWithMarker<>(xAxis2, yAxis2);
chart2.addVerticalValueMarker(new XYChart.Data<>(0,1000));

chart2.setTitle("Fund Frequency & Volume");
chart2.setCreateSymbols(false);
chart2.getData().add(series2);
chart2.getData().add(series1);

/*  スライダーの作成　*/
Slider slider = new Slider(0.00, dict.length * shiftDuration , shiftDuration);
slider.setShowTickMarks(true);
slider.setShowTickLabels(true);
slider.setMajorTickUnit(0.25f);
slider.setBlockIncrement(0.1f);

/* 各時間における具体的な値を表示する箱を作成 */
VBox vbox = new VBox();
vbox.setAlignment(Pos.CENTER);
Label label1 = new Label( "Volume	:   " + 0 + "	db"  );
Label label2 = new Label( "Vowel Identification	:   " + "なし" );
Label label3 = new Label( "Fundamental Frequency	:   " + 0 + "	Hz" );
Label label4 = new Label( "Time	:   " + 0 + "	s" );
Label label5 = new Label( "Pitch	:   " + "なし" );
label1.setFont(new Font("Arial", 20));
label2.setFont(new Font("Arial", 20));
label3.setFont(new Font("Arial", 20));
label4.setFont(new Font("Arial", 20));
label5.setFont(new Font("Arial", 20));
label1.setPrefSize(500,100);
label2.setPrefSize(500,100);
label3.setPrefSize(500,100);
label4.setPrefSize(500,100);
label5.setPrefSize(500,100);
vbox.getChildren().addAll(label1,label2,label3,label4,label5);

/* スライダーが動いた時に値やグラフ内の直線を移動させる */
slider.valueProperty().addListener((ObservableValue<? extends Number> observ, Number oldVal, Number newVal)->{
double newnum = newVal.doubleValue();
double oldnum = oldVal.doubleValue();
int num = (int) Math.round(newnum * 399  / shiftDuration / 400);
double width1 = chart1.getChartWidth();
double width2 = chart2.getChartWidth();
chart1.moveVerticalValueMarker(width1/399 * num);
chart2.moveVerticalValueMarker(width2/399 * num);
label1.setText("Volume	:   " +String.format("%.2f" ,Math.abs(app[num])) + "	db");
label2.setText("Vowel Identification	:   " + dicters[num]);
label3.setText("Fundamental Frequency	:   " + String.format("%.2f" ,fundFreqs[num]) + "	Hz");
label4.setText("Time	:   " + String.format("%.2f" ,shiftDuration * num) + "	s");
label5.setText("Pitch	:   " + Pitches[num]);
}); 

/* 全体のレイアウトを作成 */
BorderPane borderPane = new BorderPane();
BorderPane innerPane = new BorderPane();
innerPane.setRight(vbox);
innerPane.setCenter(chart2);
borderPane.setCenter(chart1);
borderPane.setTop(innerPane);
borderPane.setBottom(slider);

/* ウ イ ン ド ウ 表 示 */
primaryStage.setScene(new Scene(borderPane));
primaryStage.setTitle(getClass().getName());
primaryStage.show();

}
}
