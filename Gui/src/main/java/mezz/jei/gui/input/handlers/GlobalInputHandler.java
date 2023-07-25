package mezz.jei.gui.input.handlers;

import java.util.function.Consumer;
import mezz.jei.common.Internal;
import mezz.jei.common.input.IInternalKeyMappings;
import mezz.jei.common.network.IConnectionToServer;
import mezz.jei.common.network.packets.PacketRequestCheatPermission;
import mezz.jei.common.config.IClientToggleState;
import mezz.jei.gui.input.UserInput;
import mezz.jei.gui.input.IUserInputHandler;
import net.minecraft.client.gui.screens.Screen;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public class GlobalInputHandler implements IUserInputHandler {
	private final IClientToggleState toggleState;

	public GlobalInputHandler(IClientToggleState toggleState) {
		this.toggleState = toggleState;
	}

	@Override
	public Optional<IUserInputHandler> handleUserInput(Screen screen, UserInput input, IInternalKeyMappings keyBindings) {
		Map<Runnable, Boolean> actions = new HashMap<>() {{
			put(toggleState::toggleOverlayEnabled, input.is(keyBindings.getToggleOverlay()));
			put(toggleState::toggleBookmarkEnabled, input.is(keyBindings.getToggleBookmarkOverlay()));
			put(() -> {
				toggleState.toggleCheatItemsEnabled();
				if (toggleState.isCheatItemsEnabled()) {
					IConnectionToServer serverConnection = Internal.getServerConnection();
					serverConnection.sendPacketToServer(new PacketRequestCheatPermission());
				}
			}, input.is(keyBindings.getToggleCheatMode()));
			put(toggleState::toggleEditModeEnabled, input.is(keyBindings.getToggleEditMode()));
		}};

		Optional<Runnable> actionToPerform = actions.entrySet().stream()
				.filter(Map.Entry::getValue)
				.map(Map.Entry::getKey)
				.findFirst();

		if (!input.isSimulate()) {
			actionToPerform.ifPresent(Runnable::run);
		}

		return actionToPerform.map(action -> this);
	}
}
