public class pratice {
    String name;
    int marks;

    static int totalstudents = 0;

    pratice(String name , int marks) {
        this.name = name;
        this.marks=marks;

        totalstudents++;
    }
    void showdetails(){
        System.out.println("total student"+ totalstudents);
    }
    public static void main(String[] args ) {
        pratice s1 = new pratice("A",23);
        pratice s2 = new pratice("B",50);
        pratice s3 = new pratice("C",40);

        s1.showdetails();
        s2.showdetails();
        s3.showdetails();

          pratice.showtotalstudents();
    }
}
