/*******************************************************************************
 * Copyright 2014 Rafael Garcia Moreno.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 ******************************************************************************/
package com.bladecoder.engine.ui;

import java.text.MessageFormat;
import java.util.Locale;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.InputAdapter;
import com.badlogic.gdx.InputProcessor;
import com.badlogic.gdx.ScreenAdapter;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.Texture.TextureFilter;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.utils.viewport.ScreenViewport;
import com.badlogic.gdx.utils.viewport.Viewport;
import com.bladecoder.engine.assets.EngineAssetManager;
import com.bladecoder.engine.ui.UI.Screens;

public class HelpScreen extends ScreenAdapter implements BladeScreen {
	private final static String PIE_FILENAME = "ui/helpPie";
	private final static String DESKTOP_FILENAME = "ui/helpDesktop";

	private Texture tex;

	private UI ui;

	private String localeFilename;
	private final Viewport viewport = new ScreenViewport();

	private final InputProcessor inputProcessor = new InputAdapter() {
		@Override
		public boolean keyUp(int keycode) {
			switch (keycode) {
				case Input.Keys.ESCAPE:
				case Input.Keys.BACK:
					ui.setCurrentScreen(Screens.MENU_SCREEN);
					break;
			}

			// FIXME: This ALWAYS return true, even when we haven't dealt with the key. Is that expected?
			return true;
		}

		@Override
		public boolean touchUp(int screenX, int screenY, int pointer, int button) {
			ui.setCurrentScreen(Screens.MENU_SCREEN);
			return true;
		}
	};

	@Override
	public void render(float delta) {
		SpriteBatch batch = ui.getBatch();

		Gdx.gl.glClearColor(0, 0, 0, 1);
		Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);

		batch.setProjectionMatrix(viewport.getCamera().combined);
		batch.begin();
		batch.draw(tex, 0, 0, viewport.getScreenWidth(), viewport.getScreenHeight());
		ui.getPointer().draw(batch, viewport);
		batch.end();
	}

	@Override
	public void resize(int width, int height) {
		viewport.update(width, height, true);
	}

	@Override
	public void dispose() {
		tex.dispose();
	}

	@Override
	public void show() {
		tex = new Texture(EngineAssetManager.getInstance().getResAsset(localeFilename));
		tex.setFilter(TextureFilter.Linear, TextureFilter.Linear);

		Gdx.input.setInputProcessor(inputProcessor);
	}

	@Override
	public void hide() {
		dispose();
	}

	@Override
	public void setUI(UI ui) {
		this.ui = ui;

		final Locale locale = Locale.getDefault();

		final String filename = ui.isPieMode() ? PIE_FILENAME : DESKTOP_FILENAME;

		localeFilename = MessageFormat.format("{0}_{1}.png", filename, locale.getLanguage());

		if (!EngineAssetManager.getInstance().assetExists(localeFilename))
			localeFilename = MessageFormat.format("{0}.png", filename);
	}
}
