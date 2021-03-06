/**
 * Example comment
 * @author reichelt
 *
 */
class Test1_Anonym{
	
	public static final int y = 438;
	public int w = 48;

	/**
	 * Comment 1
	 */
	public Test() {
		// Line-comment
		int a = 3 + 5 - 8;
		System.out.println(a);
	}
	
	/**
	 * Comment 2
	 * @param i
	 */
	public static void doStaticThing(int i){
		int y = i + 1;
		System.out.println(y);
		Runnable r3 = new Runnable(){
		
		@Override
		public void run() {
			System.out.println("Run R3");
			
		}};

		
		r3.run();
	}
	
	/**
	 * Comment 3
	 */
	public void run(){
		System.out.println("a");
	}


	static Object r1 = new Runnable() {

		@Override
		public void run() {
			System.out.println("Run R1");
			
		}

		public void run2() {
			System.out.println("Run R4");
			
		}
	};
	
	static Runnable r2 = new Runnable() {
		
		@Override
		public void run() {
			System.out.println("Run R2");
		}
	};
}
