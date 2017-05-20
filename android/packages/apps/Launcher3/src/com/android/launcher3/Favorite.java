package com.android.launcher3;

public class Favorite {
	private String className;
	private String packageName;
	private long container;
	private String screen;
	private String x;
	private String y;

	public Favorite(String className, String packageName, long container,
			String screen, String x, String y) {
		this.className = className;
		this.packageName = packageName;
		this.container = container;
		this.screen = screen;
		this.x = x;
		this.y = y;
	}

	public void setClassName(String className) {
		this.className = className;
	}

	public void setPackageName(String packageName) {
		this.packageName = packageName;
	}

	public void setContainer(long container) {
		this.container = container;
	}

	public void setScreen(String screen) {
		this.screen = screen;
	}
	
	public void setX(String x) {
		this.x = x;
	}
	
	public void setY(String y) {
		this.y = y;
	}

	public String getClassName() {
		return this.className;
	}
	public String getPackageName() {
		return this.packageName;
	}

	public long getContainer() {
		return this.container;
	}

	public String getScreen() {
		return this.screen;
	}

	public String getX() {
		return this.x;
	}

	public String getY() {
		return this.y;
	}

	@Override
	public String toString() {
		return "className:" + className + ", packageName:" + packageName
				+ ", container:" + container + ", screen:" + screen + ", x:"
				+ x + ", y:" + y;
	}
}
