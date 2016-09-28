import java.util.*;
import java.lang.reflect.*;
import java.lang.Thread;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.concurrent.*;

public class Experimenter implements Runnable {

	String inputClassName, experimentClassName;
	int errorPoint, runName, experimentSize;	

	boolean experimentRunning;

	static ArrayList<Distance> finalDistancesWithScores = new ArrayList<>();

	Distance locDistance;

	static final int numThreads = 16;

	private static synchronized void addDistanceWithScores(Distance in){
		finalDistancesWithScores.add(in);
	}

	Experimenter(String inputClassName, 
		String experimentClassName,
		int runName, 
		int errorPoint,
		int experimentSize,
		boolean experimentRunning){
		this.inputClassName = inputClassName;
		this.experimentClassName = experimentClassName;
		this.errorPoint = errorPoint;
		this.runName = runName;
		this.experimentSize = experimentSize;
		this.experimentRunning = experimentRunning;
	}

	public static ReentrantReadWriteLock rwLock = new ReentrantReadWriteLock(); 

	public static ArrayList<RunId> runIds;

	public static RunId getId(RunId inId){
		rwLock.readLock().lock();
		RunId theId = runIds.get(runIds.indexOf(inId));
		rwLock.readLock().unlock();
		return theId;
	}

	public static void addId(RunId inId){
		rwLock.writeLock().lock();
		runIds.add(inId);
		rwLock.writeLock().unlock();
	}

	public static void removeId(RunId inId){
		rwLock.writeLock().lock();
		runIds.remove(runIds.indexOf(inId));
		rwLock.writeLock().unlock();
	}



	private void printAspect(){}

	private Experiment runObject(Input input, Experiment experiment, Random rand, boolean errorful, Experimenter exper){
		RunId curId = new RunId(2*runName + (errorful?1:0), 
			true,
			errorful, 
			errorPoint, 
			Thread.currentThread().getId(), 
			rand);
		addId(curId);
		experiment.experiment(input);
		RandomMethod.registerTimeCount();
		exper.locDistance = RandomMethod.getDistance(curId);
		removeId(curId);
		return experiment;
	}

	void printDistancesAndScores(Experiment correctObject, Experiment errorObject){
		locDistance.print();		
		Score[] scores = errorObject.scores(correctObject);
		for(Score s : scores){
			s.print();
		}
	}

	@Override
	public void run() {
		long seed = new Random().nextLong();
		long inputSeed = new Random().nextLong();
		/*System.out.println("Starting " + 
			Thread.currentThread().getId() + 
			" on seed: " + seed + " and input seed " + inputSeed);*/
		Random rand = new Random(inputSeed);
		Random rand1 = new Random(seed);
		Random rand2 = new Random(seed);

		RunId curId = new RunId(Thread.currentThread().getId());
		curId.setExperiment(false);
		addId(curId);
		Experiment errorObject = (Experiment)getNewObject(experimentClassName);
		Experiment correctObject = (Experiment)getNewObject(experimentClassName);
		Input iObject1 = (Input)getNewInputObject(inputClassName, experimentSize);
		Input iObject2 = (Input)getNewObject(inputClassName);
		iObject1.randomize(rand);
		iObject2.copy(iObject1);
		removeId(curId);

		correctObject = runObject(iObject1, correctObject, rand1, false, this);
		errorObject = runObject(iObject2, errorObject, rand2, true, this);
		this.locDistance.addScores(errorObject.scores(correctObject));
		addDistanceWithScores(locDistance);


		//System.out.println("The error was: " + errorObject.scores(correctObject)[0].getScore());
		
		//System.out.println("Done " + Thread.currentThread().getId());
	}

	
	static void changeToExperimentTime(Pair<Long>[] in){
		for(int i = 0; i < in.length; i ++){
			in[i].runTime = Math.min(60*60*1000/(in[i].runTime*in[i].errorPoints), (long)1000);
		}
	}

	static Pair<Long>[] getRunTimes(String inputClassName, String experimentClassName) throws InterruptedException{
		Pair<Long>[] out = new Pair[3];

		for(int q = 10, c = 0; q <= 1000; q *= 10, c ++){		
			long avgRunTime = System.currentTimeMillis();
			ArrayList<Thread> threads = new ArrayList<>();
			for(int i = 0; i < 10; i ++){
				Experimenter exp = new Experimenter(inputClassName,
					experimentClassName, i, i, q, false);
				Thread thread = new Thread(exp);
				threads.add(thread);
				thread.start();
			}
			for(Thread t : threads){
				t.join();
			}
			avgRunTime = (System.currentTimeMillis() - avgRunTime)/10;
			out[c] = new Pair<>();
			out[c].runTime = avgRunTime;
			out[c].errorPoints = (long) RandomMethod.getAverageTimeCount();;
			System.out.println("The average run time is: " + avgRunTime);
		}

		return out;
	}

	static void runExperiments(String inputClassName, String experimentClassName, Pair<Long>[] rTimes) throws InterruptedException{
		Pair<Long>[] out = new Pair[3];

		for(int q = 10, c = 0; q <= 1000; q *= 10, c ++){	
			System.out.println("Experimenting on size: " + q);	

			ArrayBlockingQueue<Runnable> threadQueue = 
				new ArrayBlockingQueue<Runnable>(100);
			ThreadPoolExecutor thePool = 
				new ThreadPoolExecutor(numThreads,
					numThreads,
					0, 
					TimeUnit.SECONDS,
					threadQueue);
			for(int i = 0; i < rTimes[c].runTime; i ++){
				for(int j = 0; j < rTimes[c].errorPoints; j ++){
					long runName = i*rTimes[c].errorPoints + j;
					if(runName % ((rTimes[c].runTime*rTimes[c].errorPoints)/10) == 0){
						System.out.println("Now on runtime: " + i + " and errorPoint " + j);
					}
					while(threadQueue.size() >= 100){					
					}

					Experimenter exp = new Experimenter(inputClassName,
						experimentClassName, 
						(int) runName, j, q, true);
					thePool.execute(exp);

					//Thread thread = new Thread(exp);
					//threads.add(thread);
					//thread.start();
				}
			}
		}
	}



	public static void main(String args[]){
		String inputClassName = args[0]; 
		String experimentClassName = args[1];	

		Experimenter.runIds = new ArrayList<>();		

		try{
			testInputObjects(inputClassName, experimentClassName);

			Pair<Long>[] rTime = getRunTimes(inputClassName, experimentClassName);
			changeToExperimentTime(rTime);
			runExperiments(inputClassName, experimentClassName, rTime);

		} catch (IllegalArgumentException E){
			System.out.println(E);
		} catch (InterruptedException E){
			System.out.println(E);
		}

	}

	private static void testInputObjects(String inputClassName, String experimentClassName) throws IllegalArgumentException {
		Object inputClass = getNewObject(inputClassName);
		Object experimentClass = getNewObject(experimentClassName);
		System.out.println(inputClass.getClass());
		System.out.println(experimentClass.getClass());
		if(!(experimentClass instanceof Experiment)) {
			throw new IllegalArgumentException();
		}
		if(!(inputClass instanceof Input)){
			throw new IllegalArgumentException();
		}
	}

	static Object getNewInputObject(String inName, int size){
		try {
			Class cls = Class.forName(inName);
			Constructor ctor = cls.getConstructor(int.class);
			Object clsInstance = ctor.newInstance(size);		
			return clsInstance;
		} catch (ClassNotFoundException E) {
			System.out.println(inName + " is not a class, please add to the classpath: " + E);
		} catch (InstantiationException E) {
			System.out.println("Trouble instantiating " + inName + ": " + E);
		} catch (IllegalAccessException E) {
			System.out.println("Trouble accessing " + inName + ": " + E);
		} catch (NoSuchMethodException E) {
			System.out.println("Trouble building random input using an input size on class" + inName + ". Is it possible that you don't have a constructor for input size or that the constructor is private?" + E);
		} catch (InvocationTargetException E) {
			System.out.println("Trouble getting random inputs:" + E);
		}
		return null;
	}

	static Object getNewObject(String inName) {
		try {
			Class cls = Class.forName(inName);
			Object clsInstance = (Object) cls.newInstance();		
			return clsInstance;
		} catch (ClassNotFoundException E) {
			System.out.println(inName + " is not a class, please add to the classpath: " + E);
		} catch (InstantiationException E) {
			System.out.println("Trouble instantiating " + inName + ": " + E);
		} catch (IllegalAccessException E) {
			System.out.println("Trouble accessing " + inName + ": " + E);
		}
		return null;
	}
}
