package lab5;

import picocli.CommandLine;

@CommandLine.Command(
    subcommands = {
        Go2WebCommand.class
    }
)
public class Go2WebMain implements Runnable {
    public static void main(String[] args) {
        CommandLine.run(new Go2WebMain(), args);
    }
    @Override
    public void run() {
        System.out.println("Welcome to Go2WebMain");
    }
}
