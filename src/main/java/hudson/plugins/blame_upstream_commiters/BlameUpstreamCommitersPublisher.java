package hudson.plugins.blame_upstream_commiters;

import hudson.Extension;
import hudson.Launcher;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.BuildListener;
import hudson.model.Result;
import hudson.tasks.ArtifactArchiver;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.BuildStepMonitor;
import hudson.tasks.MailSender;
import hudson.tasks.Mailer;
import hudson.tasks.Notifier;
import hudson.tasks.Publisher;

import java.io.File;
import java.util.ArrayList;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.apache.commons.lang.StringUtils;
import org.apache.tools.ant.types.selectors.SelectorUtils;
import org.kohsuke.stapler.DataBoundConstructor;

@SuppressWarnings({ "unchecked" })
public class BlameUpstreamCommitersPublisher extends Notifier {
	//public static final DescriptorImpl DESCRIPTOR = new DescriptorImpl();
	protected static final Logger LOGGER = Logger.getLogger(Mailer.class.getName());
	
	public boolean sendToIndividuals = false;
	
	@DataBoundConstructor
	public BlameUpstreamCommitersPublisher()
	{
	}
	
	public BuildStepMonitor getRequiredMonitorService() {
		return BuildStepMonitor.NONE;
	}
	
	@Override
	public boolean needsToRunAfterFinalized() {
        return true;
    }
	
	@Override
    public boolean perform(AbstractBuild<?,?> build, Launcher launcher, BuildListener listener) throws InterruptedException {
		if (build.getResult() != Result.SUCCESS)
		{
			ArrayList<String> recipientUpstreamProjects=this.getUpstreamRecipients(build);
			if (recipientUpstreamProjects.size() > 0) {
				String recipentString="";
				recipentString = "upstream-individuals:"+StringUtils.join(recipientUpstreamProjects, " upstream-individuals:");
				listener.getLogger().println("Upstream projects changes detected. Mailing upstream committers in the following projects:");
				listener.getLogger().println(StringUtils.join(recipientUpstreamProjects,","));

				return new MailSender(recipentString,false,sendToIndividuals) {
		            /** Check whether a path (/-separated) will be archived. */
		            @Override
		            public boolean artifactMatches(String path, AbstractBuild<?,?> build) {
		                ArtifactArchiver aa = build.getProject().getPublishersList().get(ArtifactArchiver.class);
		                if (aa == null) {
		                    LOGGER.finer("No ArtifactArchiver found");
		                    return false;
		                }
		                String artifacts = aa.getArtifacts();
		                for (String include : artifacts.split("[, ]+")) {
		                    String pattern = include.replace(File.separatorChar, '/');
		                    if (pattern.endsWith("/")) {
		                        pattern += "**";
		                    }
		                    if (SelectorUtils.matchPath(pattern, path)) {
		                        LOGGER.log(Level.FINER, "DescriptorImpl.artifactMatches true for {0} against {1}", new Object[] {path, pattern});
		                        return true;
		                    }
		                }
		                LOGGER.log(Level.FINER, "DescriptorImpl.artifactMatches for {0} matched none of {1}", new Object[] {path, artifacts});
		                return false;
		            }
		        }.execute(build,listener);
			}
		}
		return true;
	}
	
	private ArrayList<String> getUpstreamRecipients (AbstractBuild<?,?> build)
	{
		ArrayList<String> recipientList =new ArrayList<String>();
		Map <AbstractProject,Integer> upstreamBuilds = build.getUpstreamBuilds();
		
        if (upstreamBuilds != null) {
        	
        	for (AbstractProject project : upstreamBuilds.keySet()) {
        		recipientList.add(project.getName().replaceAll(" ", "\\ "));
        	}
        }

        return recipientList;
	}

    @Extension
    public static final class DescriptorImpl extends BuildStepDescriptor<Publisher> {

        public DescriptorImpl() {
            super(BlameUpstreamCommitersPublisher.class);
        }

        public String getDisplayName() {
            return "Mail upstream committers when the build fails";
        }

        public boolean isApplicable(Class<? extends AbstractProject> jobType) {
            return true;
        }
    }
}
