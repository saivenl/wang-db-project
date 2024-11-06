package com.example.mydb.backend.common;

import com.example.mydb.backend.common.dm.DataManager;
import com.example.mydb.backend.common.server.Server;
import com.example.mydb.backend.common.tbm.TableManager;
import com.example.mydb.backend.common.tm.TransactionManager;
import com.example.mydb.backend.common.utils.Panic;
import com.example.mydb.backend.common.vm.VersionManager;
import com.example.mydb.backend.common.vm.VersionManagerImpl;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import com.example.mydb.other.Error;

@SpringBootApplication
public class MydbApplication {

	public static final int port = 9999;

	public static final long DEFALUT_MEM = (1 << 20) * 64;
	public static final long KB = 1 << 10;
	public static final long MB = 1 << 20;
	public static final long GB = 1 << 30;

	public static void main(String[] args) {
		// 使用 Apache Commons CLI 的 Options 类
		Options options = new Options();
		options.addOption("open", true, "-open DBPath");
		options.addOption("create", true, "-create DBPath");
		options.addOption("mem", true, "-mem 64MB");

		CommandLineParser parser = new DefaultParser();
		CommandLine cmd;
		try {
			cmd = parser.parse(options, args);
		} catch (ParseException e) {
			System.out.println("Failed to parse command line options");
			return;
		}

		if (cmd.hasOption("open")) {
			openDB(cmd.getOptionValue("open"), parseMem(cmd.getOptionValue("mem")));
			return;
		}
		if (cmd.hasOption("create")) {
			createDB(cmd.getOptionValue("create"));
			return;
		}
		System.out.println("Usage: launcher (open|create) DBPath");
	}

	private static void createDB(String path) {
		TransactionManager tm = TransactionManager.create(path);
		DataManager dm = DataManager.create(path, DEFALUT_MEM, tm);
		VersionManager vm = new VersionManagerImpl(tm, dm);
		TableManager.create(path, vm, dm);
		tm.close();
		dm.close();
	}

	private static void openDB(String path, long mem) {
		TransactionManager tm = TransactionManager.open(path);
		DataManager dm = DataManager.open(path, mem, tm);
		VersionManager vm = new VersionManagerImpl(tm, dm);
		TableManager tbm = TableManager.open(path, vm, dm);
		new Server(port, tbm).start();
	}

	private static long parseMem(String memStr) {
		if (memStr == null || "".equals(memStr)) {
			return DEFALUT_MEM;
		}
		if (memStr.length() < 2) {
			Panic.panic(Error.InvalidMemException);
		}
		String unit = memStr.substring(memStr.length() - 2);
		long memNum = Long.parseLong(memStr.substring(0, memStr.length() - 2));
		switch (unit) {
			case "KB":
				return memNum * KB;
			case "MB":
				return memNum * MB;
			case "GB":
				return memNum * GB;
			default:
				Panic.panic(Error.InvalidMemException);
		}
		return DEFALUT_MEM;
	}
}
