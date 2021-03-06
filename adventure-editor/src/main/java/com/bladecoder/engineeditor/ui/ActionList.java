/*******************************************************************************
 * Copyright 2014 Rafael Garcia Moreno.
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *   http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 ******************************************************************************/
package com.bladecoder.engineeditor.ui;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.lang.reflect.Field;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.ui.ImageButton;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.badlogic.gdx.utils.Array;
import com.bladecoder.engine.actions.AbstractControlAction;
import com.bladecoder.engine.actions.AbstractIfAction;
import com.bladecoder.engine.actions.Action;
import com.bladecoder.engine.actions.ActorAnimationRef;
import com.bladecoder.engine.actions.CommentAction;
import com.bladecoder.engine.actions.DisableActionAction;
import com.bladecoder.engine.actions.EndAction;
import com.bladecoder.engine.actions.SceneActorRef;
import com.bladecoder.engine.model.Verb;
import com.bladecoder.engine.util.ActionUtils;
import com.bladecoder.engineeditor.Ctx;
import com.bladecoder.engineeditor.common.EditorLogger;
import com.bladecoder.engineeditor.common.ElementUtils;
import com.bladecoder.engineeditor.model.Project;
import com.bladecoder.engineeditor.ui.panels.CellRenderer;
import com.bladecoder.engineeditor.ui.panels.EditModelDialog;
import com.bladecoder.engineeditor.ui.panels.ModelList;
import com.bladecoder.engineeditor.ui.panels.ScopePanel;
import com.bladecoder.engineeditor.undo.UndoDeleteAction;

public class ActionList extends ModelList<Verb, Action> {
	private static final String CONTROL_ACTION_ID_ATTR = "caID";

	Skin skin;

	private ImageButton upBtn;
	private ImageButton downBtn;

	private ImageButton disableBtn;

	private String scope;

	private final List<Action> multiClipboard = new ArrayList<Action>();

	public ActionList(Skin skin) {
		super(skin, false);
		this.skin = skin;

		setCellRenderer(listCellRenderer);

		disableBtn = new ImageButton(skin);
		toolbar.addToolBarButton(disableBtn, "ic_eye", "Enable/Disable", "Enable/Disable");

		disableBtn.setDisabled(false);

		disableBtn.addListener(new ChangeListener() {
			@Override
			public void changed(ChangeEvent event, Actor actor) {
				toggleEnabled();
			}
		});

		upBtn = new ImageButton(skin);
		downBtn = new ImageButton(skin);

		toolbar.addToolBarButton(upBtn, "ic_up", "Move up", "Move up");
		toolbar.addToolBarButton(downBtn, "ic_down", "Move down", "Move down");
		toolbar.pack();

		list.addListener(new ChangeListener() {

			@Override
			public void changed(ChangeEvent event, Actor actor) {
				int pos = list.getSelectedIndex();

				toolbar.disableEdit(pos == -1);
				disableBtn.setDisabled(pos == -1);
				upBtn.setDisabled(pos == -1 || pos == 0);
				downBtn.setDisabled(pos == -1 || pos == list.getItems().size - 1);
			}
		});

		list.getSelection().setMultiple(true);

		upBtn.addListener(new ChangeListener() {

			@Override
			public void changed(ChangeEvent event, Actor actor) {
				up();
			}
		});

		downBtn.addListener(new ChangeListener() {
			@Override
			public void changed(ChangeEvent event, Actor actor) {
				down();
			}
		});

		Ctx.project.addPropertyChangeListener(Project.NOTIFY_ELEMENT_CREATED, new PropertyChangeListener() {
			@Override
			public void propertyChange(PropertyChangeEvent evt) {
				if (evt.getNewValue() instanceof Action && !(evt.getSource() instanceof EditActionDialog)) {
					addElements(parent, parent.getActions());
				}
			}
		});
	}

	private void toggleEnabled() {

		if (list.getSelection().size() <= 0)
			return;

		Array<Action> sel = new Array<Action>();

		for (Action a : list.getSelection().toArray()) {

			// CONTROL ACTIONS CAN'T BE DISABLED
			if (a == null || isControlAction(a))
				continue;

			Array<Action> items = list.getItems();
			int pos = items.indexOf(a, true);

			if (a instanceof DisableActionAction) {
				Action a2 = ((DisableActionAction) a).getAction();
				parent.getActions().set(pos, a2);
				items.set(pos, a2);
				sel.add(a2);
			} else {
				DisableActionAction a2 = new DisableActionAction();
				a2.setAction(a);
				parent.getActions().set(pos, a2);
				items.set(pos, a2);
				sel.add(a2);
			}
		}

		Ctx.project.setModified();
		list.getSelection().clear();
		list.getSelection().addAll(sel);
	}

	@Override
	protected EditModelDialog<Verb, Action> getEditElementDialogInstance(Action e) {
		EditActionDialog editActionDialog = new EditActionDialog(skin, parent, e, scope, list.getSelectedIndex());

		return editActionDialog;
	}

	public void setScope(String scope) {
		this.scope = scope;
	}

	@Override
	protected void create() {
		EditModelDialog<Verb, Action> dialog = getEditElementDialogInstance(null);
		dialog.show(getStage());
		dialog.setListener(new ChangeListener() {
			@SuppressWarnings("unchecked")
			@Override
			public void changed(ChangeEvent event, Actor actor) {
				int pos = list.getSelectedIndex() + 1;

				Action e = ((EditModelDialog<Verb, Action>) actor).getElement();
				list.getItems().insert(pos, e);
				parent.getActions().add(pos, e);

				list.getSelection().choose(list.getItems().get(pos));

				if (isControlAction(e)) {
					insertEndAction(pos + 1, getOrCreateControlActionId((AbstractControlAction) e));

					if (e instanceof AbstractIfAction)
						insertEndAction(pos + 2, getOrCreateControlActionId((AbstractControlAction) e));
				}

				list.invalidateHierarchy();
			}
		});
	}

	private Action editedElement;

	@Override
	protected void edit() {

		Action e = list.getSelected();

		if (e == null || e instanceof EndAction || e instanceof DisableActionAction)
			return;

		editedElement = (Action) ElementUtils.cloneElement(e);

		EditModelDialog<Verb, Action> dialog = getEditElementDialogInstance(e);
		dialog.show(getStage());
		dialog.setListener(new ChangeListener() {
			@SuppressWarnings("unchecked")
			@Override
			public void changed(ChangeEvent event, Actor actor) {
				Action e = ((EditModelDialog<Verb, Action>) actor).getElement();
				int pos = list.getSelectedIndex();
				list.getItems().set(pos, e);
				parent.getActions().set(pos, e);

				Ctx.project.setModified();

				if (isControlAction(editedElement)) {
					if (!editedElement.getClass().getName().equals(e.getClass().getName())) {

						deleteControlAction(pos, (AbstractControlAction) editedElement);

						if (isControlAction(e)) {
							insertEndAction(pos + 1,
									getOrCreateControlActionId((AbstractControlAction) e));

							if (e instanceof AbstractIfAction)
								insertEndAction(pos + 2,
										getOrCreateControlActionId((AbstractControlAction) e));
						}
					} else {
						// insert previous caId
						try {
							ActionUtils.setParam(e, CONTROL_ACTION_ID_ATTR,
									getOrCreateControlActionId((AbstractControlAction) editedElement));
						} catch (NoSuchFieldException | IllegalArgumentException | IllegalAccessException e1) {
							EditorLogger.error(e1.getMessage());
						}
					}
				}
			}
		});
	}

	private String getOrCreateControlActionId(AbstractControlAction a) {
		String id = a.getControlActionID();

		if (id == null || id.isEmpty()) {
			id = Integer.toString(MathUtils.random(1, Integer.MAX_VALUE));
			try {
				ActionUtils.setParam(a, CONTROL_ACTION_ID_ATTR, id);
			} catch (NoSuchFieldException | IllegalArgumentException | IllegalAccessException e) {
				EditorLogger.error(e.getMessage());
			}
		}

		return id;
	}

	private void insertEndAction(int pos, String id) {
		final Action e = new EndAction();

		try {
			ActionUtils.setParam(e, CONTROL_ACTION_ID_ATTR, id);
		} catch (NoSuchFieldException | IllegalArgumentException | IllegalAccessException e1) {
			EditorLogger.error(e1.getMessage());
		}

		list.getItems().insert(pos, e);
		parent.getActions().add(pos, e);
	}

	@Override
	protected void copy() {
		if (parent == null || list.getSelection().size() == 0)
			return;

		multiClipboard.clear();

		for (Action e : getSortedSelection()) {

			if (e == null || e instanceof EndAction)
				return;

			Action cloned = (Action) ElementUtils.cloneElement(e);
			multiClipboard.add(cloned);
			toolbar.disablePaste(false);

			// TRANSLATIONS
			if (scope.equals(ScopePanel.WORLD_SCOPE))
				Ctx.project.getI18N().putTranslationsInElement(cloned, true);
			else
				Ctx.project.getI18N().putTranslationsInElement(cloned, false);
		}
	}

	@Override
	protected void paste() {

		if (parent == null || multiClipboard.size() == 0)
			return;

		Array<Action> sel = new Array<Action>();

		for (int i = multiClipboard.size() - 1; i >= 0; i--) {
			Action newElement = (Action) ElementUtils.cloneElement(multiClipboard.get(i));

			int pos = list.getSelectedIndex() + 1;

			list.getItems().insert(pos, newElement);
			parent.getActions().add(pos, newElement);

			if (scope.equals(ScopePanel.WORLD_SCOPE))
				Ctx.project.getI18N().extractStrings(null, null, parent.getHashKey(), pos, newElement);
			else if (scope.equals(ScopePanel.SCENE_SCOPE))
				Ctx.project.getI18N().extractStrings(Ctx.project.getSelectedScene().getId(), null, parent.getHashKey(),
						pos, newElement);
			else
				Ctx.project.getI18N().extractStrings(Ctx.project.getSelectedScene().getId(),
						Ctx.project.getSelectedActor().getId(), parent.getHashKey(), pos, newElement);

			list.invalidateHierarchy();

			if (isControlAction(newElement)) {
				try {
					ActionUtils.setParam(newElement, CONTROL_ACTION_ID_ATTR, null);
				} catch (NoSuchFieldException | IllegalArgumentException | IllegalAccessException e) {
					EditorLogger.error(e.getMessage());
				}

				insertEndAction(pos + 1, getOrCreateControlActionId((AbstractControlAction) newElement));

				if (newElement instanceof AbstractIfAction)
					insertEndAction(pos + 2, getOrCreateControlActionId((AbstractControlAction) newElement));
			}

			sel.add(newElement);
		}

		list.getSelection().clear();
		list.getSelection().addAll(sel);
		Ctx.project.setModified();
	}

	@Override
	protected void delete() {
		if (list.getSelection().size() == 0)
			return;

		multiClipboard.clear();

		int pos = list.getSelectedIndex();

		for (Action e : getSortedSelection()) {

			if (e instanceof EndAction)
				continue;

			int pos2 = list.getItems().indexOf(e, true);
			list.getItems().removeValue(e, true);

			int idx = parent.getActions().indexOf(e);

			parent.getActions().remove(e);

			multiClipboard.add(e);

			// TRANSLATIONS
			if (scope.equals(ScopePanel.WORLD_SCOPE))
				Ctx.project.getI18N().putTranslationsInElement(e, true);
			else
				Ctx.project.getI18N().putTranslationsInElement(e, false);

			// UNDO
			Ctx.project.getUndoStack().add(new UndoDeleteAction(parent, e, idx));

			if (isControlAction(e))
				deleteControlAction(pos2, (AbstractControlAction) e);

		}

		if (list.getItems().size == 0) {
			list.getSelection().clear();
		} else if (pos >= list.getItems().size) {
			list.getSelection().choose(list.getItems().get(list.getItems().size - 1));
		} else {
			list.getSelection().choose(list.getItems().get(pos));
		}

		toolbar.disablePaste(false);

		Ctx.project.setModified();
	}

	private Array<Action> getSortedSelection() {
		Array<Action> array = list.getSelection().toArray();

		array.sort(new Comparator<Action>() {

			@Override
			public int compare(Action arg0, Action arg1) {
				Integer i0 = list.getItems().indexOf(arg0, true);
				Integer i1 = list.getItems().indexOf(arg1, true);

				return i0.compareTo(i1);
			}

		});
		
		return array;
	}

	private boolean isControlAction(Action e) {
		return e instanceof AbstractControlAction;
	}

	private void deleteControlAction(int pos, final AbstractControlAction e) {
		final String id = getOrCreateControlActionId(e);

		if (e instanceof AbstractIfAction) {
			pos = deleteFirstActionNamed(pos, id);
		}

		deleteFirstActionNamed(pos, id);
	}

	private int deleteFirstActionNamed(int pos, String actionId) {
		while (!(list.getItems().get(pos) instanceof AbstractControlAction
				&& getOrCreateControlActionId((AbstractControlAction) list.getItems().get(pos)).equals(actionId)))
			pos++;

		Action e2 = list.getItems().removeIndex(pos);
		parent.getActions().remove(e2);

		return pos;
	}

	private void up() {
		if (parent == null || list.getSelection().size() == 0)
			return;

		Array<Action> sel = new Array<Action>();

		for (Action a : getSortedSelection()) {
			
			int pos = list.getItems().indexOf(a, true);

			if (pos == -1 || pos == 0)
				return;

			Array<Action> items = list.getItems();
			Action e = items.get(pos);
			Action e2 = items.get(pos - 1);

			sel.add(e);

			if (isControlAction(e) && isControlAction(e2)) {
				continue;
			}

			parent.getActions().set(pos - 1, e);
			parent.getActions().set(pos, e2);

			items.set(pos - 1, e);
			items.set(pos, e2);
		}

		list.getSelection().clear();
		list.getSelection().addAll(sel);
		upBtn.setDisabled(list.getSelectedIndex() == 0);
		downBtn.setDisabled(list.getSelectedIndex() == list.getItems().size - 1);

		Ctx.project.setModified();
	}

	private void down() {
		if (parent == null || list.getSelection().size() == 0)
			return;

		Array<Action> sel = new Array<Action>();

		Array<Action> sortedSelection = getSortedSelection();

		for (int i = sortedSelection.size - 1; i >= 0; i--) {

			int pos = list.getItems().indexOf(sortedSelection.get(i), true);

			Array<Action> items = list.getItems();

			if (pos == -1 || pos == items.size - 1)
				return;

			Action e = items.get(pos);
			Action e2 = items.get(pos + 1);

			sel.add(e);

			if (isControlAction(e) && isControlAction(e2)) {
				continue;
			}

			parent.getActions().set(pos + 1, e);
			parent.getActions().set(pos, e2);

			items.set(pos + 1, e);
			items.set(pos, e2);
		}

		list.getSelection().clear();
		list.getSelection().addAll(sel);
		upBtn.setDisabled(list.getSelectedIndex() == 0);
		downBtn.setDisabled(list.getSelectedIndex() == list.getItems().size - 1);

		Ctx.project.setModified();
	}

	// -------------------------------------------------------------------------
	// ListCellRenderer
	// -------------------------------------------------------------------------
	private final CellRenderer<Action> listCellRenderer = new CellRenderer<Action>() {

		@Override
		protected String getCellTitle(Action a) {
			boolean enabled = true;

			if (a instanceof CommentAction) {
				String comment = null;
				
				try {
					comment = ActionUtils.getStringValue(a, "comment");
				} catch (NoSuchFieldException | IllegalArgumentException | IllegalAccessException e) {
					EditorLogger.error(e.getMessage());
				}
				
				if(comment == null)
					comment = "COMMENT";
				else if(comment.indexOf('\n') != -1) {
					comment = comment.substring(0, comment.indexOf('\n'));
				}
				
				return "[YELLOW]" + comment + "[]";
			}
			
			if (a instanceof DisableActionAction) {
				a = ((DisableActionAction) a).getAction();
				enabled = false;
			}

			String id = ActionUtils.getName(a.getClass());

			if (id == null)
				id = a.getClass().getCanonicalName();

			Field field = ActionUtils.getField(a.getClass(), "actor");
			String actor = null;

			if (field != null) {
				try {
					field.setAccessible(true);
					Object v = field.get(a);
					if (v != null)
						actor = v.toString();
				} catch (IllegalArgumentException | IllegalAccessException e) {
					EditorLogger.error(e.getMessage());
				}
			}

			boolean animationAction = id.equals("Animation");
			boolean controlAction = isControlAction(a);

			if (!enabled && !controlAction) {
				if (actor != null && !animationAction) {
					SceneActorRef sa = new SceneActorRef(actor);

					if (sa.getSceneId() != null)
						id = MessageFormat.format("[GRAY]{0} {1}.{2}[]", sa.getSceneId(), sa.getActorId(), id);
					else
						id = MessageFormat.format("[GRAY]{0}.{1}[]", sa.getActorId(), id);
				} else if (animationAction) {
					field = ActionUtils.getField(a.getClass(), "animation");
					String animation = null;

					if (field != null) {
						try {
							field.setAccessible(true);
							animation = field.get(a).toString();
						} catch (IllegalArgumentException | IllegalAccessException e) {
							EditorLogger.error(e.getMessage());
						}
					}

					ActorAnimationRef aa = new ActorAnimationRef(animation);

					if (aa.getActorId() != null)
						id = MessageFormat.format("[GRAY]{0}.{1} {2}[]", aa.getActorId(), id, aa.getAnimationId());
					else
						id = MessageFormat.format("[GRAY]{0} {1}[]", id, aa.getAnimationId());
				} else {
					id = MessageFormat.format("[GRAY]{0}[]", id);
				}

			} else if (actor != null && !animationAction && !controlAction) {
				SceneActorRef sa = new SceneActorRef(actor);

				if (sa.getSceneId() != null)
					id = MessageFormat.format("[GREEN]{0}[] {1}.{2}", sa.getSceneId(), sa.getActorId(), id);
				else
					id = MessageFormat.format("{0}.{1}", sa.getActorId(), id);
			} else if (animationAction) {
				field = ActionUtils.getField(a.getClass(), "animation");
				String animation = null;

				if (field != null) {
					try {
						field.setAccessible(true);
						animation = field.get(a).toString();
					} catch (IllegalArgumentException | IllegalAccessException e) {
						EditorLogger.error(e.getMessage());
					}
				}

				ActorAnimationRef aa = new ActorAnimationRef(animation);

				if (aa.getActorId() != null)
					id = MessageFormat.format("[GREEN]{0}.{1} {2}[]", aa.getActorId(), id, aa.getAnimationId());
				else
					id = MessageFormat.format("[GREEN]{0} {1}[]", id, aa.getAnimationId());
			} else if (controlAction) {
				if (a instanceof EndAction) {
					Action parentAction = findParentAction((EndAction) a);

					if (parentAction instanceof AbstractIfAction
							&& isElse((AbstractIfAction) parentAction, (EndAction) a)) {
						id = "Else";
					} else {
						id = "End" + ActionUtils.getName(parentAction.getClass());
					}
				}

				if (actor != null) {
					SceneActorRef sa = new SceneActorRef(actor);

					if (sa.getSceneId() != null)
						id = MessageFormat.format("[GREEN]{0}[] [BLUE]{1}.{2}[]", sa.getSceneId(), sa.getActorId(), id);
					else
						id = MessageFormat.format("[BLUE]{0}.{1}[BLUE]", sa.getActorId(), id);
				} else
					id = MessageFormat.format("[BLUE]{0}[]", id);
			}

			return id;
		}

		private boolean isElse(AbstractIfAction parentAction, EndAction ea) {
			final String caID = ea.getControlActionID();
			ArrayList<Action> actions = parent.getActions();

			int idx = actions.indexOf(parentAction);

			for (int i = idx + 1; i < actions.size(); i++) {
				Action aa = actions.get(i);

				if (isControlAction(aa) && ((AbstractControlAction) aa).getControlActionID().equals(caID)) {
					if (aa == ea)
						return true;

					return false;
				}
			}

			return false;
		}

		private Action findParentAction(EndAction a) {
			final String caID = a.getControlActionID();
			ArrayList<Action> actions = parent.getActions();

			for (Action a2 : actions) {
				if (isControlAction(a2) && ((AbstractControlAction) a2).getControlActionID().equals(caID)) {
					return a2;
				}
			}

			return null;
		}

		@Override
		protected String getCellSubTitle(Action a) {
			if (a instanceof CommentAction)
				return "";
			
			if (a instanceof DisableActionAction)
				a = ((DisableActionAction) a).getAction();

			StringBuilder sb = new StringBuilder();

			String[] params = ActionUtils.getFieldNames(a);
			String actionName = ActionUtils.getName(a.getClass());

			for (String p : params) {

				if (p.equals("actor")
						|| (actionName != null && actionName.equals("Animation") && p.equals("animation")))
					continue;
				
				if(p.equals("caID"))
					continue;

				Field f = ActionUtils.getField(a.getClass(), p);

				try {
					final boolean accessible = f.isAccessible();
					f.setAccessible(true);
					Object o = f.get(a);
					if (o == null)
						continue;
					String v = o.toString();

					// Check world Scope for translations
					if (scope.equals(ScopePanel.WORLD_SCOPE))
						sb.append(p).append(": ")
								.append(Ctx.project.getI18N().getWorldTranslation(v).replace("\n", "|")).append(' ');
					else
						sb.append(p).append(": ").append(Ctx.project.translate(v).replace("\n", "|")).append(' ');

					f.setAccessible(accessible);
				} catch (IllegalArgumentException | IllegalAccessException e) {
					EditorLogger.error(e.getMessage());
				}
			}

			return sb.toString();
		}

		@Override
		protected boolean hasSubtitle() {
			return true;
		}
	};
}
