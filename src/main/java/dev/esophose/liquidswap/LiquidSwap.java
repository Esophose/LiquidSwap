package dev.esophose.liquidswap;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.PacketType.Play.Server;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.ProtocolManager;
import com.comphenix.protocol.events.ListenerPriority;
import com.comphenix.protocol.events.PacketAdapter;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.events.PacketEvent;
import com.comphenix.protocol.reflect.StructureModifier;
import com.comphenix.protocol.wrappers.MultiBlockChangeInfo;
import com.comphenix.protocol.wrappers.WrappedBlockData;
import com.github.steveice10.mc.protocol.data.game.chunk.Chunk;
import com.github.steveice10.mc.protocol.data.game.world.block.BlockState;
import com.github.steveice10.packetlib.io.buffer.ByteBufferNetInput;
import com.github.steveice10.packetlib.io.buffer.ByteBufferNetOutput;
import java.io.IOException;
import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.bukkit.Material;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * @author Esophose
 */
@SuppressWarnings("deprecation")
public class LiquidSwap extends JavaPlugin {

    private static final int WATER_HIGH = 34;
    private static final int WATER_LOW = 42;
    private static final int LAVA_HIGH = 50;
    private static final int LAVA_LOW = 58;
    private static final int LIQUID_DIFF = LAVA_HIGH - WATER_HIGH;

    @Override
    public void onEnable() {
        ProtocolManager protocolManager = ProtocolLibrary.getProtocolManager();

        protocolManager.addPacketListener(new PacketAdapter(this, ListenerPriority.NORMAL,
                Server.BLOCK_ACTION,
                Server.BLOCK_BREAK,
                Server.BLOCK_CHANGE,
                Server.MULTI_BLOCK_CHANGE,
                Server.MAP_CHUNK) {
            @Override
            public void onPacketSending(PacketEvent event) {
                PacketContainer packet = event.getPacket();

                if (event.getPacketType() == Server.BLOCK_CHANGE || event.getPacketType() == Server.BLOCK_BREAK) {
                    WrappedBlockData blockData = packet.getBlockData().read(0);
                    if (blockData.getType() == Material.LAVA) {
                        packet.getBlockData().write(0, WrappedBlockData.createData(Material.WATER, blockData.getData()));
                    } else if (blockData.getType() == Material.WATER) {
                        packet.getBlockData().write(0, WrappedBlockData.createData(Material.LAVA, blockData.getData()));
                    }
                } else if (event.getPacketType() == Server.MULTI_BLOCK_CHANGE) {
                    StructureModifier<MultiBlockChangeInfo[]> infoModifier = packet.getMultiBlockChangeInfoArrays();
                    MultiBlockChangeInfo[] infoArray = infoModifier.read(0);
                    for (MultiBlockChangeInfo info : infoArray) {
                        WrappedBlockData blockData = info.getData();
                        if (blockData.getType() == Material.LAVA) {
                            info.setData(WrappedBlockData.createData(Material.WATER, blockData.getData()));
                        } else if (blockData.getType() == Material.WATER) {
                            info.setData(WrappedBlockData.createData(Material.LAVA, blockData.getData()));
                        }
                    }
                    packet.getMultiBlockChangeInfoArrays().write(0, infoArray);
                } else if (event.getPacketType() == Server.MAP_CHUNK) {
                    byte[] mapChunkData = packet.getByteArrays().read(0);

                    try {
                        ByteBuffer inputBuffer = ByteBuffer.wrap(mapChunkData);
                        ByteBufferNetOutput output = new ByteBufferNetOutput(ByteBuffer.allocate(mapChunkData.length * 2));

                        while (inputBuffer.hasRemaining()) {
                            Chunk chunk = Chunk.read(new ByteBufferNetInput(inputBuffer));

                            Map<Integer, BlockState> swaps = new HashMap<>();

                            List<BlockState> states = chunk.getStates();
                            for (BlockState state : states) {
                                int id = state.getId();
                                if (LAVA_HIGH <= id && id <= LAVA_LOW) {
                                    swaps.put(states.indexOf(state), new BlockState(state.getId() - LIQUID_DIFF));
                                } else if (WATER_HIGH <= id && id <= WATER_LOW) {
                                    swaps.put(states.indexOf(state), new BlockState(state.getId() + LIQUID_DIFF));
                                }
                            }

                            swaps.forEach(states::set);
                            Chunk.write(output, chunk);
                        }

                        output.flush();
                        ByteBuffer outputBuffer = output.getByteBuffer();
                        ((Buffer) outputBuffer).clear();
                        byte[] modifiedMapChunkData = new byte[outputBuffer.capacity()];
                        output.getByteBuffer().get(modifiedMapChunkData, 0, modifiedMapChunkData.length);
                        packet.getByteArrays().write(0, modifiedMapChunkData);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }
        });
    }

}
