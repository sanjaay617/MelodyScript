import java.util.ArrayList;
import java.util.List;

public class ASTNodes {

    public interface ASTNode {
        String toPrettyString(String indent);
    }

    public static class ProgramNode implements ASTNode {
        public final List<ASTNode> statements = new ArrayList<>();

        @Override
        public String toPrettyString(String indent) {
            StringBuilder sb = new StringBuilder();
            sb.append(indent).append("ProgramNode\n");
            for (ASTNode stmt : statements) {
                sb.append(stmt.toPrettyString(indent + "|-- "));
            }
            return sb.toString();
        }

        @Override
        public String toString() {
            return toPrettyString("");
        }
    }

    public static class TempoNode implements ASTNode {
        public final int tempo;

        public TempoNode(int tempo) {
            this.tempo = tempo;
        }

        @Override
        public String toPrettyString(String indent) {
            return indent + "TempoNode (value: " + tempo + ")\n";
        }
    }

    public static class InstrumentNode implements ASTNode {
        public final String instrument;

        public InstrumentNode(String instrument) {
            this.instrument = instrument;
        }

        @Override
        public String toPrettyString(String indent) {
            return indent + "InstrumentNode (name: \"" + instrument + "\")\n";
        }
    }

    public static class PhraseDefNode implements ASTNode {
        public final String identifier;
        public final NoteSequenceNode sequence;

        public PhraseDefNode(String identifier, NoteSequenceNode sequence) {
            this.identifier = identifier;
            this.sequence = sequence;
        }

        @Override
        public String toPrettyString(String indent) {
            StringBuilder sb = new StringBuilder();
            sb.append(indent).append("PhraseDefNode (identifier: \"").append(identifier).append("\")\n");
            sb.append(sequence.toPrettyString(indent + "|   "));
            return sb.toString();
        }
    }

    public static class NoteNode implements ASTNode {
        public final String pitch;
        public final String duration;

        public NoteNode(String pitch, String duration) {
            this.pitch = pitch;
            this.duration = duration;
        }

        @Override
        public String toPrettyString(String indent) {
            return indent + "NoteNode (pitch: \"" + pitch + "\", duration: \"" + duration + "\")\n";
        }
    }

    public static class NoteSequenceNode implements ASTNode {
        public final List<NoteNode> notes = new ArrayList<>();

        @Override
        public String toPrettyString(String indent) {
            StringBuilder sb = new StringBuilder();
            sb.append(indent).append("NoteSequenceNode\n");
            for (NoteNode note : notes) {
                sb.append(note.toPrettyString(indent + "|   "));
            }
            return sb.toString();
        }
    }

    public static class PlayNode implements ASTNode {
        public final String target;
        public final RepeatNode repeatNode;

        public PlayNode(String target) {
            this.target = target;
            this.repeatNode = null;
        }

        public PlayNode(RepeatNode repeatNode) {
            this.target = null;
            this.repeatNode = repeatNode;
        }

        @Override
        public String toPrettyString(String indent) {
            StringBuilder sb = new StringBuilder();
            if (target != null) {
                sb.append(indent).append("PlayNode (target: \"").append(target).append("\")\n");
            } else {
                sb.append(indent).append("PlayNode\n");
                sb.append(repeatNode.toPrettyString(indent + "|   "));
            }
            return sb.toString();
        }
    }

    public static class RepeatNode implements ASTNode {
        public final int count;
        public final NoteSequenceNode sequence;

        public RepeatNode(int count, NoteSequenceNode sequence) {
            this.count = count;
            this.sequence = sequence;
        }

        @Override
        public String toPrettyString(String indent) {
            StringBuilder sb = new StringBuilder();
            sb.append(indent).append("RepeatNode (count: ").append(count).append(")\n");
            sb.append(sequence.toPrettyString(indent + "|   "));
            return sb.toString();
        }
    }
}